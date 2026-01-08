package com.factcheck.collector.integration.ingestion.fetcher;

import com.factcheck.collector.integration.ingestion.RobotsService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

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
        when(resp.url()).thenReturn(new java.net.URL(url));

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
        when(resp.header("ETag")).thenReturn(null);
        when(resp.header("Last-Modified")).thenReturn(null);
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.body()).thenReturn("<html><body>Attention Required - verify you are human</body></html>");
        when(resp.url()).thenReturn(new java.net.URL(url));

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

        // Duplicate p1 and remove promo/nav/footer content.
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
        when(resp.url()).thenReturn(new java.net.URL(url));
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        // Set quality gates for a deterministic test.
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

        // Paragraph p1 appears only once after dedupe.
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
        when(resp.contentType()).thenReturn("text/html; charset=utf-8");
        when(resp.url()).thenReturn(new java.net.URL(url));
        when(resp.parse()).thenReturn(doc);

        when(jsoupClient.execute(anyString(), anyString(), anyInt(), anyInt())).thenReturn(resp);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService, jsoupClient);
        setDefaults(extractor);
        // Keep the gate strict so it fails.
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

    private void setDefaults(GenericJsoupContentExtractor extractor) {
        ReflectionTestUtils.setField(extractor, "userAgent", "TestAgent/1.0");
        ReflectionTestUtils.setField(extractor, "timeout", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(extractor, "maxBodySizeBytes", 1024 * 1024);
        // Leave defaults for quality gates unless overridden.
    }
}