package com.wovely.products.security;

import java.security.Key;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/** Validates JWTs issued by the core service (shared HS256 secret). */
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${wovely.app.jwtSecret}")
    private String jwtSecret;

    private Key key;

    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("wovely.app.jwtSecret is not set (env JWT_SECRET)");
        }
        // Throws WeakKeyException for secrets shorter than 256 bit
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /** Parses and verifies the token; empty if invalid, expired or tampered. */
    @SuppressWarnings("unchecked")
    public Optional<JwtPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            List<String> roles = claims.get("roles", List.class);
            return Optional.of(new JwtPrincipal(
                    claims.get("uid", String.class),
                    claims.getSubject(),
                    roles != null ? List.copyOf(roles) : List.of()));
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
