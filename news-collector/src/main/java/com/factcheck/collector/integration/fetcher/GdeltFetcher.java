package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.exception.FetchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class GdeltFetcher implements SourceFetcher {

    @Override
    public List<RawArticle> fetch(SourceEndpoint sourceEndpoint) throws FetchException {
        throw new FetchException("GdeltFetcher not implemented", null);
    }

    @Override
    public boolean supports(SourceEndpoint sourceEndpoint) {
        if (sourceEndpoint.getKind() != SourceKind.API) {
            return false;
        }
        String provider = sourceEndpoint.getApiProvider();
        return provider != null && provider.equalsIgnoreCase("gdelt");
    }
}
