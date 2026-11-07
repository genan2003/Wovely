package com.wovely.wovely.services;

import java.time.Duration;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.PenaltyRequest;
import com.wovely.wovely.repository.UserRepository;

/**
 * Strikes, suspensions and bans (docs/diagrams/state/account-status.puml).
 *
 * <ul>
 *   <li>A suspension always has an end: {@code suspendDays} 1–{@value #MAX_SUSPEND_DAYS} is required.</li>
 *   <li>Suspending or banning signs the user out everywhere (refresh tokens revoked, PLAN.md 2.9).</li>
 *   <li>Expired suspensions are written back to ACTIVE by {@link #liftExpiredSuspensions()}; until then
 *       {@link User#effectiveStatus} and the security check already treat them as active.</li>
 * </ul>
 */
@Service
public class UserPenaltyService {

    public static final int MAX_SUSPEND_DAYS = 365;
    private static final Logger log = LoggerFactory.getLogger(UserPenaltyService.class);

    private final UserRepository users;
    private final MongoTemplate mongo;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public UserPenaltyService(UserRepository users, MongoTemplate mongo, RefreshTokenService refreshTokens,
            AuditService audit) {
        this.users = users;
        this.mongo = mongo;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    public User apply(String userId, PenaltyRequest request) {
        if (request == null || request.getAction() == null) {
            throw ApiException.badRequest("action is required (STRIKE or STATUS)");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        String before = "strikes=" + user.getStrikes() + ", status=" + user.getAccountStatus();

        switch (request.getAction()) {
            case "STRIKE" -> user.setStrikes(user.getStrikes() + 1);
            case "STATUS" -> applyStatus(user, request.getNewStatus(), request.getSuspendDays());
            default -> throw ApiException.badRequest("action must be STRIKE or STATUS");
        }

        users.save(user);
        if (user.getAccountStatus() != EAccountStatus.ACTIVE) {
            refreshTokens.revokeAll(user.getId());
        }
        audit.record("USER_PENALTY", userId, request.getAction() + ": " + before + " -> strikes=" + user.getStrikes()
                + ", status=" + user.getAccountStatus()
                + (user.getSuspendedUntil() != null ? ", until " + user.getSuspendedUntil().toInstant() : ""));
        return user;
    }

    private static void applyStatus(User user, EAccountStatus status, Integer suspendDays) {
        if (status == null) {
            throw ApiException.badRequest("newStatus is required");
        }
        if (status == EAccountStatus.SUSPENDED) {
            if (suspendDays == null || suspendDays < 1 || suspendDays > MAX_SUSPEND_DAYS) {
                throw ApiException.badRequest("suspendDays must be between 1 and " + MAX_SUSPEND_DAYS);
            }
            user.setSuspendedUntil(Date.from(new Date().toInstant().plus(Duration.ofDays(suspendDays))));
        } else {
            user.setSuspendedUntil(null);
        }
        user.setAccountStatus(status);
    }

    /** Writes expired suspensions back to ACTIVE (one atomic multi-update; safe to run on several instances). */
    @Scheduled(fixedDelayString = "${penalties.expiry-check-ms:300000}", initialDelayString = "${penalties.expiry-check-ms:300000}")
    public long liftExpiredSuspensions() {
        Date now = new Date();
        Query expired = new Query(Criteria.where("accountStatus").is(EAccountStatus.SUSPENDED)
                .and("suspendedUntil").lte(now));
        UpdateResult result = mongo.updateMulti(expired,
                new Update().set("accountStatus", EAccountStatus.ACTIVE).unset("suspendedUntil"), User.class);
        long lifted = result.getModifiedCount();
        if (lifted > 0) {
            log.info("Lifted {} expired suspension(s)", lifted);
            audit.record("SUSPENSIONS_EXPIRED", "users", lifted + " suspension(s) ended and were set to ACTIVE");
        }
        return lifted;
    }
}
