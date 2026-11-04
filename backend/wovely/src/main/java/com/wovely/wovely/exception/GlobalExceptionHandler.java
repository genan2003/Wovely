package com.wovely.wovely.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/** Maps exceptions to the uniform {@link ErrorBody}; never leaks internal messages for unexpected errors. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e, HttpServletRequest req) {
        return respond(e.getStatus(), e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Validation failed" : msg, req);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<Map<String, Object>> unreadable(Exception e, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> unauthenticated(AuthenticationException e, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "Authentication required", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "Not found", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest req) {
        logger.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
    }

    private static ResponseEntity<Map<String, Object>> respond(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorBody.of(status, message, req.getRequestURI()));
    }
}
