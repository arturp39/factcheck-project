package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.integration.robots.RobotsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericJsoupContentExtractor implements ArticleContentExtractor {

    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final int MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024; // 2MB; tune
    private static final int MIN_TEXT_CHARS = 1500; // tune per your needs
    private static final int MIN_PARAGRAPHS = 5;

    private static final Set<String> BOILERPLATE_ANCESTOR_TAGS = Set.of(
            "nav", "footer", "header", "aside", "form", "button", "dialog", "noscript"
    );

    private static final Pattern CAPTCHA_PATTERN = Pattern.compile(
            "(captcha|verify you are human|attention required|cloudflare|bot detection)",
            Pattern.CASE_INSENSITIVE
    );

    private final RobotsService robotsService;

    @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
    private String userAgent;

    @Override
    public ArticleFetchResult fetchAndExtract(String url) {
        if (!robotsService.isAllowed(url)) {
            log.info("Skipping {} (robots.txt disallows)", url);
            return ArticleFetchResult.builder()
                    .fetchError("Robots.txt disallows fetching")
                    .build();
        }

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .ignoreHttpErrors(true) // so we can inspect body on 403
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.8,*;q=0.5")
                    .execute();

            Instant fetchedAt = Instant.now();
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

            if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
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
            doc.select("script, style, nav, footer, header, aside, form, noscript").remove();

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

        } catch (SocketTimeoutException e) {
            log.warn("Timeout while fetching article {}", url);
            return ArticleFetchResult.builder()
                    .fetchError("Timeout while fetching article")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to fetch/parse article {}", url, e);
            return ArticleFetchResult.builder()
                    .fetchError("Failed to fetch or parse article: " + e.getMessage())
                    .build();
        }
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
            Elements ps = c.select("> p");
            if (ps.size() < 3) continue;

            int score = 0;
            for (Element p : ps) {
                String t = p.text();
                if (t != null) score += t.length();
            }
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
        if (lower.contains("accept cookies") || lower.contains("subscribe") || lower.contains("sign in")) return true;

        return false;
    }

    private boolean passesQualityGate(String text, int paragraphCount) {
        if (paragraphCount < MIN_PARAGRAPHS) return false;
        if (text == null) return false;
        if (text.length() < MIN_TEXT_CHARS) return false;

        // crude "is this mostly punctuation-free garbage?" check
        long letters = text.chars().filter(Character::isLetter).count();
        return letters > (text.length() * 0.5);
    }

    private String safeSnippet(String body, int maxChars) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, maxChars);
    }
}