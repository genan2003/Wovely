package com.wovely.wovely.models;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Opaque refresh token (PLAN.md 2.9). Only the SHA-256 hash is stored; the raw value is
 * returned once to the client. Tokens rotate on every use and are revoked on sign-out,
 * ban and suspension.
 */
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt = Instant.now();

    public RefreshToken() {
    }

    public RefreshToken(String tokenHash, String userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public String getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isUsable(Instant now) {
        return !revoked && expiresAt != null && expiresAt.isAfter(now);
    }
}
