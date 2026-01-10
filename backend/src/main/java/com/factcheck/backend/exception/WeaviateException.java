package com.factcheck.backend.exception;

public class WeaviateException extends RuntimeException {

    public WeaviateException(String message) {
        super(message);
    }

    public WeaviateException(String message, Throwable cause) {
        super(message, cause);
    }
}
