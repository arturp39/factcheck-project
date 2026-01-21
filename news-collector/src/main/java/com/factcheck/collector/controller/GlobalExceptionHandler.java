package com.factcheck.collector.controller;

import com.factcheck.collector.dto.ErrorResponse;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.exception.IngestionRunAlreadyRunningException;
import com.factcheck.collector.exception.MbfcQuotaExceededException;
import com.factcheck.collector.exception.NewsApiRateLimitException;
import com.factcheck.collector.exception.NlpServiceException;
import com.factcheck.collector.exception.ProcessingFailedException;
import com.factcheck.collector.exception.WeaviateException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MbfcQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleMbfcQuota(MbfcQuotaExceededException ex, HttpServletRequest request) {
        return buildWarn(ex, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(NewsApiRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleNewsApiRateLimit(NewsApiRateLimitException ex, HttpServletRequest request) {
        return buildWarn(ex, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler({NlpServiceException.class})
    public ResponseEntity<ErrorResponse> handleNlp(NlpServiceException ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({WeaviateException.class})
    public ResponseEntity<ErrorResponse> handleWeaviate(WeaviateException ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler({FetchException.class})
    public ResponseEntity<ErrorResponse> handleFetch(FetchException ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler({ProcessingFailedException.class})
    public ResponseEntity<ErrorResponse> handleProcessing(ProcessingFailedException ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({IngestionRunAlreadyRunningException.class})
    public ResponseEntity<ErrorResponse> handleIngestionRunning(IngestionRunAlreadyRunningException ex,
                                                               HttpServletRequest request) {
        return buildWarn(ex, request, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> build(Exception ex,
                                                HttpServletRequest request,
                                                HttpStatus status) {
        String correlationId = MDC.get("corrId");
        String path = request.getRequestURI();
        log.error("Request failed status={} path={} corrId={}", status.value(), path, correlationId, ex);

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path,
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ErrorResponse> buildWarn(Exception ex,
                                                    HttpServletRequest request,
                                                    HttpStatus status) {
        String correlationId = MDC.get("corrId");
        String path = request.getRequestURI();
        log.warn("Request failed status={} path={} corrId={}", status.value(), path, correlationId, ex);

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path,
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }
}
