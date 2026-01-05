package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.exception.FetchException;

import java.util.List;

public interface SourceFetcher {

    List<RawArticle> fetch(SourceEndpoint sourceEndpoint) throws FetchException;

    boolean supports(SourceEndpoint sourceEndpoint);
}