package com.factcheck.collector.exception;

public class IngestionRunAlreadyRunningException extends RuntimeException {
    public IngestionRunAlreadyRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}