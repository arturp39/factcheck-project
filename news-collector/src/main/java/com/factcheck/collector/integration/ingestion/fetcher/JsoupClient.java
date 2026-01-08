package com.factcheck.collector.integration.ingestion.fetcher;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class JsoupClient {

    public Connection.Response execute(String url, String userAgent, int timeoutMs, int maxBodySizeBytes) throws Exception {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .followRedirects(true)
                .maxBodySize(maxBodySizeBytes)
                .ignoreHttpErrors(true)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.8,*;q=0.5")
                .execute();
    }
}