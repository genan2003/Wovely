package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.wovely.wovely.models.RefreshToken;
import com.wovely.wovely.repository.RefreshTokenRepository;

/** PLAN.md 2.9 — opaque, hashed, rotating refresh tokens. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repository;
    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, 60_000);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void onlyTheHashIsStored() {
        String raw = service.issue("u-1");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(raw).isEqualTo(RefreshTokenService.hash(raw));
        assertThat(saved.getValue().getUserId()).isEqualTo("u-1");
        assertThat(raw).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void rotationRevokesOldTokenAndIssuesNewOne() {
        RefreshToken stored = new RefreshToken(RefreshTokenService.hash("raw"), "u-1", Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(stored));
        var rotation = service.rotate("raw").orElseThrow();
        assertThat(rotation.userId()).isEqualTo("u-1");
        assertThat(rotation.newToken()).isNotEqualTo("raw");
        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken stored = new RefreshToken(RefreshTokenService.hash("raw"), "u-1", Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(stored));
        assertThat(service.rotate("raw")).isEmpty();
    }

    @Test
    void reusingARotatedTokenRevokesAllTokensOfTheUser() {
        RefreshToken reused = new RefreshToken(RefreshTokenService.hash("raw"), "u-1", Instant.now().plusSeconds(60));
        reused.setRevoked(true);
        RefreshToken other = new RefreshToken("h2", "u-1", Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(reused));
        when(repository.findByUserIdAndRevokedFalse("u-1")).thenReturn(List.of(other));
        assertThat(service.rotate("raw")).isEmpty();
        assertThat(other.isRevoked()).isTrue();
    }

    @Test
    void blankOrUnknownTokenIsRejected() {
        assertThat(service.rotate(" ")).isEmpty();
        assertThat(service.rotate("unknown")).isEmpty();
    }
}
