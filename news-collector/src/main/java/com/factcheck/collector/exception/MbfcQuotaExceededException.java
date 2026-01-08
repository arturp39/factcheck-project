package com.factcheck.collector.exception;

public class MbfcQuotaExceededException extends RuntimeException {
    public MbfcQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}