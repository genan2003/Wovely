package com.wovely.wovely.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wovely.wovely.models.RefreshToken;
import com.wovely.wovely.repository.RefreshTokenRepository;

/** Issues, rotates and revokes refresh tokens (PLAN.md 2.9). */
@Service
public class RefreshTokenService {

    /** Result of a successful rotation: the owner and the new raw token. */
    public record Rotation(String userId, String newToken) {
    }

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final Duration validity;

    public RefreshTokenService(RefreshTokenRepository repository,
            @Value("${wovely.app.refreshExpirationMs:604800000}") long refreshExpirationMs) {
        this.repository = repository;
        this.validity = Duration.ofMillis(refreshExpirationMs);
    }

    /** Creates a new refresh token for the user and returns the raw value (shown once). */
    public String issue(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(hash(raw), userId, Instant.now().plus(validity)));
        return raw;
    }

    /**
     * Validates and rotates: the presented token is revoked and a new one issued.
     * Re-use of an already rotated token revokes all of the user's tokens (theft detection).
     */
    public java.util.Optional<Rotation> rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findByTokenHash(hash(raw)).flatMap(token -> {
            if (token.isRevoked()) {
                revokeAll(token.getUserId());
                return java.util.Optional.empty();
            }
            if (!token.isUsable(Instant.now())) {
                return java.util.Optional.empty();
            }
            token.setRevoked(true);
            repository.save(token);
            return java.util.Optional.of(new Rotation(token.getUserId(), issue(token.getUserId())));
        });
    }

    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(raw)).ifPresent(t -> {
            t.setRevoked(true);
            repository.save(t);
        });
    }

    /** Signs the user out everywhere (used on ban/suspension). */
    public void revokeAll(String userId) {
        List<RefreshToken> active = repository.findByUserIdAndRevokedFalse(userId);
        active.forEach(t -> t.setRevoked(true));
        repository.saveAll(active);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
