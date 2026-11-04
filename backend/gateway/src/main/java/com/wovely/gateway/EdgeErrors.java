package com.wovely.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/** Same JSON error shape as the services' GlobalExceptionHandler: {status, error, message, path, timestamp}. */
final class EdgeErrors {

    private EdgeErrors() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\""
                + message.replace("\"", "'") + "\",\"path\":\"" + exchange.getRequest().getPath().value().replace("\"", "")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
