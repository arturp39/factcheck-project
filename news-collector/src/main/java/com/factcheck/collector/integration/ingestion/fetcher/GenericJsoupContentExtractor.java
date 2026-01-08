package com.factcheck.collector.integration.ingestion.fetcher;

import com.factcheck.collector.integration.ingestion.RobotsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericJsoupContentExtractor implements ArticleContentExtractor {

    private static final Set<String> REMOVE_TAGS = Set.of(
            "script", "style", "nav", "footer", "header", "aside", "form", "noscript"
    );

    private static final Set<String> BOILERPLATE_ANCESTOR_TAGS = Set.of(
            "nav", "footer", "header", "aside", "form", "button", "dialog", "noscript"
    );

    private static final Pattern CAPTCHA_PATTERN = Pattern.compile(
            "(captcha|verify you are human|attention required|cloudflare|bot detection)",
            Pattern.CASE_INSENSITIVE
    );

    private final RobotsService robotsService;
    private final JsoupClient jsoupClient;

    @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
    private String userAgent;

    @Value("${crawler.timeout:PT10S}")
    private Duration timeout;

    @Value("${crawler.max-body-bytes:2097152}") // 2 MB max.
    private int maxBodySizeBytes;

    @Value("${crawler.min-text-chars:1500}")
    private int minTextChars;

    @Value("${crawler.min-paragraphs:5}")
    private int minParagraphs;

    @Override
    public ArticleFetchResult fetchAndExtract(String url) {
        if (!robotsService.isAllowed(url)) {
            log.info("Skipping {} (robots.txt disallows)", url);
            return ArticleFetchResult.builder()
                    .fetchedAt(Instant.now())
                    .finalUrl(url)
                    .fetchError("Robots.txt disallows fetching")
                    .build();
        }

        Instant fetchedAt = Instant.now();

        try {
            Connection.Response response = jsoupClient.execute(
                    url,
                    userAgent,
                    Math.toIntExact(timeout.toMillis()),
                    maxBodySizeBytes
            );

            int statusCode = response.statusCode();
            String etag = response.header("ETag");
            String lastModified = response.header("Last-Modified");
            String contentType = response.contentType();
            String finalUrl = response.url() != null ? response.url().toString() : url;

            if (statusCode < 200 || statusCode >= 300) {
                String bodySnippet = safeSnippet(response.body(), 2000);

                boolean looksBlocked = statusCode == 403 || statusCode == 429 || statusCode == 503
                        || CAPTCHA_PATTERN.matcher(bodySnippet).find();

                return ArticleFetchResult.builder()
                        .fetchedAt(fetchedAt)
                        .httpStatus(statusCode)
                        .httpEtag(etag)
                        .httpLastModified(lastModified)
                        .finalUrl(finalUrl)
                        .fetchError(looksBlocked ? "Blocked/Rate-limited/CAPTCHA suspected" : "HTTP status " + statusCode)
                        .blockedSuspected(looksBlocked)
                        .build();
            }

            if (!isHtmlContentType(contentType)) {
                log.info("Skipping non-HTML content {} contentType={}", finalUrl, contentType);
                return ArticleFetchResult.builder()
                        .fetchedAt(fetchedAt)
                        .httpStatus(statusCode)
                        .httpEtag(etag)
                        .httpLastModified(lastModified)
                        .finalUrl(finalUrl)
                        .extractionError("Non-HTML contentType: " + contentType)
                        .build();
            }

            Document doc = response.parse();
            removeBoilerplateTags(doc);

            Element container = selectMainContainer(doc);
            List<String> paragraphs = extractCleanParagraphs(container);

            String text = String.join("\n\n", paragraphs);
            if (!passesQualityGate(text, paragraphs.size())) {
                log.warn("Low-quality extraction for {} (chars={}, paras={})", finalUrl, text.length(), paragraphs.size());
                return ArticleFetchResult.builder()
                        .fetchedAt(fetchedAt)
                        .httpStatus(statusCode)
                        .httpEtag(etag)
                        .httpLastModified(lastModified)
                        .finalUrl(finalUrl)
                        .extractionError("Low-quality extraction (likely boilerplate or dynamic page)")
                        .build();
            }

            return ArticleFetchResult.builder()
                    .fetchedAt(fetchedAt)
                    .httpStatus(statusCode)
                    .httpEtag(etag)
                    .httpLastModified(lastModified)
                    .finalUrl(finalUrl)
                    .extractedText(text)
                    .build();

        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("Timeout while fetching article {}", url);
                return ArticleFetchResult.builder()
                        .fetchedAt(fetchedAt)
                        .finalUrl(url)
                        .fetchError("Timeout while fetching article")
                        .build();
            }

            log.warn("Failed to fetch/parse article {}", url, e);
            return ArticleFetchResult.builder()
                    .fetchedAt(fetchedAt)
                    .finalUrl(url)
                    .fetchError("Failed to fetch or parse article: " + safeMessage(e))
                    .build();
        }
    }

    private void removeBoilerplateTags(Document doc) {
        // Build a selector from the tag set.
        String selector = String.join(",", REMOVE_TAGS);
        doc.select(selector).remove();
    }

    private boolean isHtmlContentType(String contentType) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("text/html") || ct.contains("application/xhtml+xml");
    }

    private Element selectMainContainer(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null) return article;

        Element bodyProp = doc.selectFirst("[itemprop=articleBody]");
        if (bodyProp != null) return bodyProp;

        Element main = doc.selectFirst("main");
        if (main != null) return main;

        Elements candidates = doc.select("div, section");
        Element best = doc.body();
        int bestScore = 0;

        for (Element c : candidates) {
            Elements ps = c.select("p"); // Include nested paragraphs.
            if (ps.size() < 3) continue;

            int score = 0;
            int counted = 0;
            for (Element p : ps) {
                String t = p.text();
                if (t != null && !t.isBlank()) {
                    score += t.length();
                    counted++;
                }
                // Cap to avoid oversized containers.
                if (counted >= 30) break;
            }

            // Penalize deep containers.
            int depthPenalty = Math.min(10, c.parents().size());
            score = score - (depthPenalty * 50);

            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        return best;
    }

    private List<String> extractCleanParagraphs(Element container) {
        Elements paragraphElements = container.select("p");
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (Element p : paragraphElements) {
            if (isBoilerplate(p)) continue;

            String text = p.text();
            if (text == null) continue;

            text = text.trim();
            if (text.length() < 40) continue;

            if (seen.add(text)) {
                result.add(text);
            }
        }

        return result;
    }

    private boolean isBoilerplate(Element p) {
        Element cur = p;
        int depth = 0;
        while (cur != null && depth < 8) {
            String tag = cur.tagName();
            if (BOILERPLATE_ANCESTOR_TAGS.contains(tag)) return true;
            cur = cur.parent();
            depth++;
        }

        String text = p.text();
        if (text == null || text.isBlank()) return true;

        int textLen = text.length();
        int linkTextLen = p.select("a").text().length();
        double linkDensity = textLen == 0 ? 1.0 : (double) linkTextLen / (double) textLen;
        if (linkDensity > 0.35) return true;

        String ctx = (p.className() + " " + p.id()).toLowerCase(Locale.ROOT);
        String[] markers = {
                "subscribe", "newsletter", "cookie", "advert", "sponsor", "promo",
                "related", "recommended", "comments", "share", "social", "signin", "sign-in"
        };
        for (String m : markers) {
            if (ctx.contains(m)) return true;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("accept cookies") || lower.contains("subscribe") || lower.contains("sign in");
    }

    private boolean passesQualityGate(String text, int paragraphCount) {
        if (paragraphCount < minParagraphs) return false;
        if (text == null) return false;
        if (text.length() < minTextChars) return false;

        long letters = text.chars().filter(Character::isLetter).count();
        return letters > (text.length() * 0.5);
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) return false;
        if (t instanceof SocketTimeoutException) return true;
        if (t instanceof IOException io && io.getMessage() != null) {
            String m = io.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("timed out") || m.contains("timeout")) return true;
        }
        return isTimeout(t.getCause());
    }

    private String safeSnippet(String body, int maxChars) {
        if (body == null) return "";
        String s = body;
        if (s.length() > maxChars) s = s.substring(0, maxChars);
        return s.replaceAll("\\s+", " ").trim();
    }

    private String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}