package com.example.demo.exception;

public class WeaviateException extends RuntimeException {

    public WeaviateException(String message) {
        super(message);
    }

    public WeaviateException(String message, Throwable cause) {
        super(message, cause);
    }
}
