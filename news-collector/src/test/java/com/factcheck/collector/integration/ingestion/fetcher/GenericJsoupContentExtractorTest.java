package com.factcheck.collector.integration.ingestion.fetcher;

import com.factcheck.collector.integration.ingestion.RobotsService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenericJsoupContentExtractorTest {

    @Test
    void fetchAndExtract_returnsErrorWhenDisallowedByRobots() {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/page";
        when(robotsService.isAllowed(url)).thenReturn(false);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getFetchError()).containsIgnoringCase("robots");
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
        assertThat(result.getFetchedAt()).isNotNull();

        verifyNoInteractions(jsoupClient);
    }

    @Test
    void fetchAndExtract_returnsExtractionErrorWhenNonHtmlContentType() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/file.pdf";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("etag");
        when(resp.header("Last-Modified")).thenReturn("lm");
        when(resp.contentType()).thenReturn("application/pdf");
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractionError()).containsIgnoringCase("non-html");
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
    }

    @Test
    void fetchAndExtract_marksBlockedWhenHttpStatusAndCaptchaMarker() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/protected";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(403);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.body()).thenReturn("<html><body>Attention Required - verify you are human</body></html>");
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(403);
        assertThat(result.getFetchError()).isNotBlank();
        assertThat(result.getBlockedSuspected()).isEqualTo(Boolean.TRUE);
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
    }

    @Test
    void fetchAndExtract_httpStatusNon2xx_notBlocked_setsHttpStatusErrorMessage() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/missing";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(404);
        when(resp.header("ETag")).thenReturn("etag");
        when(resp.header("Last-Modified")).thenReturn("lm");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.body()).thenReturn("<html><body>Not found</body></html>");
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(404);
        assertThat(result.getBlockedSuspected()).isEqualTo(Boolean.FALSE);
        assertThat(result.getFetchError()).isEqualTo("HTTP status 404");
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
    }

    @Test
    void fetchAndExtract_httpStatus400_marksBlockedWhenCaptchaMarkerInBody() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/weird";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(400);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.body()).thenReturn("<html><body>captcha</body></html>");
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(400);
        assertThat(result.getBlockedSuspected()).isEqualTo(Boolean.TRUE);
        assertThat(result.getFetchError()).containsIgnoringCase("blocked");
    }

    @Test
    void fetchAndExtract_httpStatus503_marksBlockedEvenWhenBodyNull() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/temp-down";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(503);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html");
        when(resp.body()).thenReturn(null);
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(503);
        assertThat(result.getBlockedSuspected()).isEqualTo(Boolean.TRUE);
        assertThat(result.getFetchError()).containsIgnoringCase("blocked");
        assertThat(result.getExtractedText()).isNull();
    }

    @Test
    void fetchAndExtract_nonHtmlWhenContentTypeNull() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/no-ct";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn(null);
        when(resp.url()).thenReturn(URI.create(url).toURL());

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getExtractionError()).isEqualTo("Non-HTML contentType: null");
    }

    @Test
    void fetchAndExtract_success_prefersResponseFinalUrlOverInputUrl() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/redirect";
        String finalUrl = "https://example.com/final";
        when(robotsService.isAllowed(url)).thenReturn(true);

        String filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(25).trim();
        String p1 = "Para one. " + filler;
        String p2 = "Para two. " + filler;
        String p3 = "Para three. " + filler;
        String p4 = "Para four. " + filler;
        String p5 = "Para five. " + filler;

        String html = """
                <html><body>
                  <article>
                    <p>%s</p><p>%s</p><p>%s</p><p>%s</p><p>%s</p>
                  </article>
                </body></html>
                """.formatted(p1, p2, p3, p4, p5);

        Document doc = Jsoup.parse(html, finalUrl);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("etag");
        when(resp.header("Last-Modified")).thenReturn("lm");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(finalUrl).toURL());
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        ReflectionTestUtils.setField(extractor, "minParagraphs", 5);
        ReflectionTestUtils.setField(extractor, "minTextChars", 300);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getFinalUrl()).isEqualTo(finalUrl);
        assertThat(result.getExtractedText()).isNotBlank();
    }

    @Test
    void fetchAndExtract_success_filtersLinkDenseParagraphsAndShortParagraphs() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/page";
        when(robotsService.isAllowed(url)).thenReturn(true);

        String filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(25).trim();
        String good1 = "Good paragraph one. " + filler;
        String good2 = "Good paragraph two. " + filler;
        String good3 = "Good paragraph three. " + filler;
        String good4 = "Good paragraph four. " + filler;
        String good5 = "Good paragraph five. " + filler;

        String html = """
                <html>
                  <body>
                    <article>
                      <p>Too short.</p>
                      <p><a href="x">click</a> <a href="y">click</a> <a href="z">click</a> <a href="w">click</a> <a href="v">click</a></p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                    </article>
                  </body>
                </html>
                """.formatted(good1, good2, good3, good4, good5);

        Document doc = Jsoup.parse(html, url);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(url).toURL());
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        ReflectionTestUtils.setField(extractor, "minParagraphs", 5);
        ReflectionTestUtils.setField(extractor, "minTextChars", 300);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractionError()).isNull();
        assertThat(result.getExtractedText()).contains(good1);
        assertThat(result.getExtractedText().toLowerCase(Locale.ROOT)).doesNotContain("too short");
    }

    @Test
    void fetchAndExtract_success_selectsBestDivCandidateWhenNoArticleMain() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/page";
        when(robotsService.isAllowed(url)).thenReturn(true);

        String filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(25).trim();
        String shortP = "Short block. " + filler.substring(0, 60);
        String longP1 = "Long paragraph one. " + filler;
        String longP2 = "Long paragraph two. " + filler;
        String longP3 = "Long paragraph three. " + filler;
        String longP4 = "Long paragraph four. " + filler;
        String longP5 = "Long paragraph five. " + filler;

        String html = """
                <html><body>
                  <div id="header"><p>%s</p><p>%s</p><p>%s</p></div>
                  <div id="content">
                    <p>%s</p><p>%s</p><p>%s</p><p>%s</p><p>%s</p>
                  </div>
                </body></html>
                """.formatted(shortP, shortP, shortP, longP1, longP2, longP3, longP4, longP5);

        Document doc = Jsoup.parse(html, url);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(url).toURL());
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        ReflectionTestUtils.setField(extractor, "minParagraphs", 5);
        ReflectionTestUtils.setField(extractor, "minTextChars", 300);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractedText()).contains(longP5);
    }

    @Test
    void fetchAndExtract_success_skipsBoilerplateAndDuplicates_andPassesQualityGate() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/page";
        when(robotsService.isAllowed(url)).thenReturn(true);

        String filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(30).trim();
        String p1 = "Main paragraph one. " + filler;
        String p2 = "Main paragraph two. " + filler;
        String p3 = "Main paragraph three. " + filler;
        String p4 = "Main paragraph four. " + filler;
        String p5 = "Main paragraph five. " + filler;
        String p6 = "Main paragraph six. " + filler;

        String html = """
                <html>
                  <body>
                    <nav>Subscribe now</nav>
                    <article>
                      <p>%s</p>
                      <p class="promo">Promo text</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p>%s</p>
                    </article>
                    <footer>Footer</footer>
                  </body>
                </html>
                """.formatted(p1, p1, p2, p3, p4, p5, p6);

        Document doc = Jsoup.parse(html, url);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("etag");
        when(resp.header("Last-Modified")).thenReturn("lm");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(url).toURL());
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        ReflectionTestUtils.setField(extractor, "minParagraphs", 5);
        ReflectionTestUtils.setField(extractor, "minTextChars", 400);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getFetchError()).isNull();
        assertThat(result.getExtractionError()).isNull();
        assertThat(result.getExtractedText()).isNotBlank();

        assertThat(result.getExtractedText()).contains(p1);
        assertThat(result.getExtractedText()).contains(p5);

        assertThat(result.getExtractedText().toLowerCase(Locale.ROOT)).doesNotContain("promo text");
        assertThat(result.getExtractedText().toLowerCase(Locale.ROOT)).doesNotContain("subscribe now");

        assertThat(result.getExtractedText().indexOf(p1))
                .isEqualTo(result.getExtractedText().lastIndexOf(p1));
    }

    @Test
    void fetchAndExtract_returnsLowQualityExtractionError_whenQualityGateFails() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/thin";
        when(robotsService.isAllowed(url)).thenReturn(true);

        String html = """
                <html><body>
                  <article>
                    <p>This is short.</p>
                    <p>This is also short.</p>
                  </article>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, url);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(url).toURL());
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        ReflectionTestUtils.setField(extractor, "minParagraphs", 5);
        ReflectionTestUtils.setField(extractor, "minTextChars", 1500);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getExtractionError()).containsIgnoringCase("low-quality");
    }

    @Test
    void fetchAndExtract_returnsTimeoutFetchError_whenUnderlyingThrowsSocketTimeout() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/slow";
        when(robotsService.isAllowed(url)).thenReturn(true);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getFetchError()).containsIgnoringCase("timeout");
        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
        assertThat(result.getFetchedAt()).isNotNull();
    }

    @Test
    void fetchAndExtract_recognizesTimeoutFromCauseChain() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/slow2";
        when(robotsService.isAllowed(url)).thenReturn(true);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException(new SocketTimeoutException("timed out")));

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getFetchError()).containsIgnoringCase("timeout");
        assertThat(result.getExtractedText()).isNull();
    }

    @Test
    void fetchAndExtract_returnsGenericFetchError_whenParseThrowsNonTimeout() throws Exception {
        RobotsService robotsService = mock(RobotsService.class);
        JsoupClient jsoupClient = mock(JsoupClient.class);

        String url = "https://example.com/bad-html";
        when(robotsService.isAllowed(url)).thenReturn(true);

        Connection.Response resp = mock(Connection.Response.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.header("ETag")).thenReturn("");
        when(resp.header("Last-Modified")).thenReturn("");
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(URI.create(url).toURL());
        when(resp.parse()).thenThrow(new IOException("broken stream"));

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);

        ArticleFetchResult result = extractor.fetchAndExtract(url);

        assertThat(result.getExtractedText()).isNull();
        assertThat(result.getFinalUrl()).isEqualTo(url);
        assertThat(result.getFetchError()).contains("Failed to fetch or parse article");
        assertThat(result.getFetchError()).contains("broken stream");
    }

    private void setDefaults(GenericJsoupContentExtractor extractor) {
        ReflectionTestUtils.setField(extractor, "userAgent", "TestAgent/1.0");
        ReflectionTestUtils.setField(extractor, "timeout", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(extractor, "maxBodySizeBytes", 1024 * 1024);
    }
}