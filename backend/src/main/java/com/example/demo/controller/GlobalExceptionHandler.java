package com.example.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class})
    public Object handleBadRequest(Exception ex, HttpServletRequest request, Model model) {
        return buildResponse(ex, request, model, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object handleValidation(Exception ex, HttpServletRequest request, Model model) {
        return buildResponse(ex, request, model, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        return buildResponse(ex, request, model, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Object buildResponse(Exception ex,
                                 HttpServletRequest request,
                                 Model model,
                                 HttpStatus status) {

        String correlationId = MDC.get("corrId");
        String path = request.getRequestURI();
        log.error("Request failed status={} path={} corrId={}", status.value(), path, correlationId, ex);

        if (isHtmlRequest(request)) {
            model.addAttribute("error", ex.getMessage() == null
                    ? "Unexpected error"
                    : ex.getMessage());
            return "index";
        }

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path,
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String path = request.getRequestURI();
        boolean wantsHtml = accept != null && accept.contains("text/html");
        boolean isViewRoute = path != null && (path.equals("/") || path.startsWith("/verify") || path.startsWith("/followup") || path.startsWith("/bias"));
        return wantsHtml || isViewRoute;
    }
}