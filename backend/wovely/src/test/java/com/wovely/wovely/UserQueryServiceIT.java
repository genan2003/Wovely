package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.services.UserQueryService;
import com.wovely.wovely.support.AbstractMongoIT;

/** PLAN.md 3.1 — admin user search runs in MongoDB (real Mongo). */
class UserQueryServiceIT extends AbstractMongoIT {

    @Autowired UserQueryService service;
    @Autowired UserRepository repository;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private User save(String username, String email, String fullName, EAccountStatus status) {
        User u = new User(username + tag, email.replace("@", tag + "@"), "x");
        u.setFullName(fullName);
        u.setAccountStatus(status);
        return repository.save(u);
    }

    @Test
    void matchesUsernameEmailOrFullNameCaseInsensitively() {
        save("alice", "a@x.test", "Alice Weaver", EAccountStatus.ACTIVE);
        save("bob", "potter@x.test", "Bob Clay", EAccountStatus.ACTIVE);
        save("carol", "c@x.test", "Carol POTTER", EAccountStatus.BANNED);

        assertThat(service.search("ALICE" + tag, null)).extracting(User::getUsername).containsExactly("alice" + tag);
        assertThat(service.search("potter", null)).extracting(User::getUsername)
            .contains("bob" + tag, "carol" + tag).doesNotContain("alice" + tag);
        assertThat(service.search("potter", EAccountStatus.BANNED)).extracting(User::getUsername)
            .contains("carol" + tag).doesNotContain("bob" + tag);
    }

    @Test
    void regexMetacharactersAreTreatedLiterally() {
        save("dot", "d@x.test", "D", EAccountStatus.ACTIVE);
        assertThat(service.search(".*", null)).isEmpty();
        assertThat(service.search("dot" + tag, null)).hasSize(1);
    }
}
