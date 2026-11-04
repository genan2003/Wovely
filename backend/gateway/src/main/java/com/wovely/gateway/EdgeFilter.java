package com.wovely.gateway;

import java.security.Key;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * First thing every request meets (PLAN.md 4.1):
 * <ol>
 *   <li>correlation id — reuse a safe incoming {@code X-Correlation-Id} or create one, forward and echo it;</li>
 *   <li>{@code /internal/**} is never reachable from outside (404), and a client-sent internal API key is
 *       stripped so it can't be smuggled to a service;</li>
 *   <li>a bearer token, if present, must be valid (signature + expiry) — otherwise 401 here, before any
 *       service is hit. Authorisation (roles, ownership) stays in the services.</li>
 * </ol>
 * The user id of a valid token is stored for the rate-limit key resolver.
 */
@Component
public class EdgeFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    public static final String USER_ID_ATTR = "wovely.uid";
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private static final Logger logger = LoggerFactory.getLogger(EdgeFilter.class);

    private final Key key;

    public EdgeFilter(@Value("${wovely.app.jwtSecret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("wovely.app.jwtSecret is not set (env JWT_SECRET)");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String incoming = request.getHeaders().getFirst(CORRELATION_HEADER);
        String correlation = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlation);

        String path = request.getPath().value();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return EdgeErrors.write(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                        .parseClaimsJws(auth.substring(7)).getBody();
                String uid = claims.get("uid", String.class);
                if (uid != null) {
                    exchange.getAttributes().put(USER_ID_ATTR, uid);
                }
            } catch (JwtException | IllegalArgumentException e) {
                logger.debug("[{}] rejected token at the edge: {}", correlation, e.getMessage());
                return EdgeErrors.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
        }

        var mutated = request.mutate().headers(h -> {
            h.set(CORRELATION_HEADER, correlation);
            h.remove(INTERNAL_KEY_HEADER);
        }).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
