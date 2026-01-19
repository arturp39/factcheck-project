package com.factcheck.backend.exception;

public class VertexServiceException extends RuntimeException {
    public VertexServiceException(String message) {
        super(message);
    }

    public VertexServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
