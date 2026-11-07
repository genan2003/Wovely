package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.services.UserPenaltyService;
import com.wovely.wovely.services.UserQueryService;
import com.wovely.wovely.support.AbstractMongoIT;

/** Suspensions end on their own: status filter and the expiry job against real Mongo. */
class AccountPenaltyIT extends AbstractMongoIT {

    @Autowired UserRepository repository;
    @Autowired UserQueryService query;
    @Autowired UserPenaltyService penalties;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private User suspended(String name, Duration fromNow) {
        User u = new User(name + tag, name + tag + "@x.test", "x");
        u.setAccountStatus(EAccountStatus.SUSPENDED);
        u.setSuspendedUntil(fromNow == null ? null : Date.from(Instant.now().plus(fromNow)));
        return repository.save(u);
    }

    @Test
    void statusFilterUsesTheEffectiveStatus() {
        suspended("ended", Duration.ofHours(-2));
        suspended("running", Duration.ofDays(3));
        suspended("legacy", null);

        assertThat(query.search(tag, EAccountStatus.SUSPENDED)).extracting(User::getUsername)
            .containsExactlyInAnyOrder("running" + tag, "legacy" + tag);
        assertThat(query.search(tag, EAccountStatus.ACTIVE)).extracting(User::getUsername)
            .containsExactly("ended" + tag);
    }

    @Test
    void expiryJobLiftsOnlyEndedSuspensions() {
        User ended = suspended("ended", Duration.ofMinutes(-1));
        User running = suspended("running", Duration.ofDays(1));
        User legacy = suspended("legacy", null);

        assertThat(penalties.liftExpiredSuspensions()).isGreaterThanOrEqualTo(1);

        User e = repository.findById(ended.getId()).orElseThrow();
        assertThat(e.getAccountStatus()).isEqualTo(EAccountStatus.ACTIVE);
        assertThat(e.getSuspendedUntil()).isNull();
        assertThat(repository.findById(running.getId()).orElseThrow().getAccountStatus()).isEqualTo(EAccountStatus.SUSPENDED);
        assertThat(repository.findById(legacy.getId()).orElseThrow().getAccountStatus()).isEqualTo(EAccountStatus.SUSPENDED);
    }
}
