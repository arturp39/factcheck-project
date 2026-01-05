package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.integration.robots.RobotsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericJsoupContentExtractorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchAndExtract_skipsBoilerplateAndDuplicates() throws Exception {
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
                  </body>
                </html>
                """.formatted(p1, p1, p2, p3, p4, p5, p6);
        server.createContext("/page", exchange -> {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        RobotsService robotsService = mock(RobotsService.class);
        when(robotsService.isAllowed(baseUrl + "/page")).thenReturn(true);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService);
        ReflectionTestUtils.setField(extractor, "userAgent", "TestAgent/1.0");

        ArticleFetchResult result = extractor.fetchAndExtract(baseUrl + "/page");

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getExtractedText()).contains(p1);
        assertThat(result.getExtractedText()).contains(p5);
        assertThat(result.getExtractedText()).doesNotContain("Promo");
        assertThat(result.getExtractedText().indexOf(p1))
                .isEqualTo(result.getExtractedText().lastIndexOf(p1));
    }

    @Test
    void fetchAndExtract_returnsErrorWhenDisallowedByRobots() {
        RobotsService robotsService = mock(RobotsService.class);
        when(robotsService.isAllowed(baseUrl + "/page")).thenReturn(false);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService);
        ArticleFetchResult result = extractor.fetchAndExtract(baseUrl + "/page");

        assertThat(result.getFetchError()).contains("Robots.txt");
        assertThat(result.getExtractedText()).isNull();
    }
}