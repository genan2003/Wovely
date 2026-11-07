package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.PenaltyRequest;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;

/** docs/diagrams/state/account-status.puml */
@ExtendWith(MockitoExtension.class)
class UserPenaltyServiceTest {

    @Mock UserRepository users;
    @Mock MongoTemplate mongo;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuditService audit;
    @InjectMocks UserPenaltyService service;

    User user;

    @BeforeEach
    void data() {
        user = new User("mara", "mara@example.com", "hash");
        user.setId("u-1");
        lenient().when(users.findById("u-1")).thenReturn(Optional.of(user));
        lenient().when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    static PenaltyRequest status(EAccountStatus s, Integer days) {
        PenaltyRequest r = new PenaltyRequest();
        r.setAction("STATUS");
        r.setNewStatus(s);
        r.setSuspendDays(days);
        return r;
    }

    static Date in(Duration d) {
        return Date.from(Instant.now().plus(d));
    }

    @Test
    void suspensionAlwaysHasAnEnd() {
        service.apply("u-1", status(EAccountStatus.SUSPENDED, 14));
        assertThat(user.getAccountStatus()).isEqualTo(EAccountStatus.SUSPENDED);
        assertThat(user.getSuspendedUntil()).isAfter(in(Duration.ofDays(13))).isBefore(in(Duration.ofDays(15)));
        verify(refreshTokens).revokeAll("u-1");
    }

    @Test
    void suspensionWithoutAValidLengthIsRejected() {
        for (Integer days : new Integer[] { null, 0, -1, UserPenaltyService.MAX_SUSPEND_DAYS + 1 }) {
            assertThatThrownBy(() -> service.apply("u-1", status(EAccountStatus.SUSPENDED, days)))
                .isInstanceOf(ApiException.class).extracting("status").hasToString("400 BAD_REQUEST");
        }
        assertThat(user.getAccountStatus()).isEqualTo(EAccountStatus.ACTIVE);
        verify(users, never()).save(any());
    }

    @Test
    void restoreAndBanClearTheEndDate() {
        user.setAccountStatus(EAccountStatus.SUSPENDED);
        user.setSuspendedUntil(in(Duration.ofDays(3)));
        service.apply("u-1", status(EAccountStatus.ACTIVE, null));
        assertThat(user.getSuspendedUntil()).isNull();
        verify(refreshTokens, never()).revokeAll(anyString());

        user.setSuspendedUntil(in(Duration.ofDays(3)));
        service.apply("u-1", status(EAccountStatus.BANNED, 30)); // days are ignored for a ban
        assertThat(user.getSuspendedUntil()).isNull();
        verify(refreshTokens).revokeAll("u-1");
    }

    @Test
    void strikesCountUpAndAreAudited() {
        PenaltyRequest strike = new PenaltyRequest();
        strike.setAction("STRIKE");
        service.apply("u-1", strike);
        assertThat(user.getStrikes()).isEqualTo(1);
        verify(audit).record(eq("USER_PENALTY"), eq("u-1"), org.mockito.ArgumentMatchers.contains("strikes=1"));
    }

    @Test
    void badRequestsAndUnknownUsers() {
        assertThatThrownBy(() -> service.apply("u-1", null)).isInstanceOf(ApiException.class);
        PenaltyRequest smite = new PenaltyRequest();
        smite.setAction("SMITE");
        assertThatThrownBy(() -> service.apply("u-1", smite)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.apply("u-1", status(null, null))).isInstanceOf(ApiException.class);
        when(users.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply("ghost", smite))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("404 NOT_FOUND");
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void expiredSuspensionsAreLiftedInOneUpdateAndAudited() {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(User.class)))
            .thenReturn(UpdateResult.acknowledged(2, 2L, null));
        assertThat(service.liftExpiredSuspensions()).isEqualTo(2);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(User.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q.get("accountStatus")).isEqualTo(EAccountStatus.SUSPENDED);
        assertThat(((Document) q.get("suspendedUntil")).get("$lte")).isInstanceOf(Date.class);
        Document u = update.getValue().getUpdateObject();
        assertThat(((Document) u.get("$set")).get("accountStatus")).isEqualTo(EAccountStatus.ACTIVE);
        assertThat((Document) u.get("$unset")).containsKey("suspendedUntil");
        verify(audit).record(eq("SUSPENSIONS_EXPIRED"), eq("users"), anyString());
    }

    @Test
    void nothingExpiredMeansNoAuditNoise() {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(User.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThat(service.liftExpiredSuspensions()).isZero();
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------ what "suspended" means right now

    @Test
    void effectiveStatusTreatsAnEndedSuspensionAsActive() {
        Date now = new Date();
        user.setAccountStatus(EAccountStatus.SUSPENDED);
        user.setSuspendedUntil(in(Duration.ofHours(-1)));
        assertThat(user.effectiveStatus(now)).isEqualTo(EAccountStatus.ACTIVE);
        user.setSuspendedUntil(in(Duration.ofHours(1)));
        assertThat(user.effectiveStatus(now)).isEqualTo(EAccountStatus.SUSPENDED);
        user.setSuspendedUntil(null); // legacy: no end date
        assertThat(user.effectiveStatus(now)).isEqualTo(EAccountStatus.SUSPENDED);
    }

    @Test
    void legacySuspensionWithoutAnEndStaysLockedEndedOneUnlocks() {
        user.setAccountStatus(EAccountStatus.SUSPENDED);
        user.setRoles(new java.util.HashSet<>());
        user.setSuspendedUntil(null);
        assertThat(UserDetailsImpl.build(user).isAccountNonLocked()).isFalse(); // was "true" before: no lock at all
        user.setSuspendedUntil(in(Duration.ofMinutes(-1)));
        assertThat(UserDetailsImpl.build(user).isAccountNonLocked()).isTrue();
        user.setSuspendedUntil(in(Duration.ofDays(1)));
        assertThat(UserDetailsImpl.build(user).isAccountNonLocked()).isFalse();
    }
}
