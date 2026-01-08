package com.factcheck.collector.integration.catalog.mbfc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MbfcApiEntry {

    @JsonProperty("Source")
    private String sourceName;

    @JsonProperty("MBFC URL")
    private String mbfcUrl;

    @JsonProperty("Bias")
    private String bias;

    @JsonProperty("Country")
    private String country;

    @JsonProperty("Factual Reporting")
    private String factualReporting;

    @JsonProperty("Media Type")
    private String mediaType;

    @JsonProperty("Source URL")
    private String sourceUrl;

    @JsonProperty("Credibility")
    private String credibility;

    @JsonProperty("Source ID#")
    private Long sourceId;
}