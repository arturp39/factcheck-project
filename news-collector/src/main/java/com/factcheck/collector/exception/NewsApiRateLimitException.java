package com.factcheck.collector.exception;

import lombok.Getter;

@Getter
public class NewsApiRateLimitException extends RuntimeException {

    private final Integer retryAfterSeconds;

    public NewsApiRateLimitException(String message, Integer retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
