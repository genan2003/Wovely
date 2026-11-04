package com.wovely.products.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Uniform error payload: {@code {status, error, message, path, timestamp}}. */
public final class ErrorBody {

    private ErrorBody() {
    }

    public static Map<String, Object> of(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    /** Minimal JSON serialisation for the security handlers (no ObjectMapper needed). */
    public static String json(HttpStatus status, String message, String path) {
        return "{\"status\":" + status.value()
                + ",\"error\":\"" + escape(status.getReasonPhrase())
                + "\",\"message\":\"" + escape(message)
                + "\",\"path\":\"" + escape(path)
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
