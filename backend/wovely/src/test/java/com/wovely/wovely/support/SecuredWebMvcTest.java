package com.wovely.wovely.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.config.CorsConfig;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.security.WebSecurityConfig;
import com.wovely.wovely.security.jwt.AuthEntryPointJwt;
import com.wovely.wovely.security.jwt.JwtUtils;
import com.wovely.wovely.security.services.UserDetailsServiceImpl;

/**
 * Base for {@code @WebMvcTest} slices of the core service with the <b>real</b> security setup:
 * JWT filter, method security and ownership rules. No Docker/Mongo needed.
 *
 * <p>The core service resolves the JWT subject through {@link UserRepository}; this base stubs it
 * with five users whose ids match {@link TestTokens}: testuser/buyer-1, buyer2/buyer-2,
 * seller/seller-1, seller2/seller-2, admin/admin-1. Use e.g.
 * {@code mvc.perform(get(..).header(AUTH, bearer(TestTokens.buyer())))}.
 */
@ActiveProfiles("test")
@Import({ WebSecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, UserDetailsServiceImpl.class,
        OwnershipGuard.class, CorsConfig.class })
public abstract class SecuredWebMvcTest {

    protected static final String AUTH = "Authorization";

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected UserRepository userRepository;

    protected final Map<String, User> users = Map.of(
            "testuser", user(TestTokens.BUYER_ID, "testuser", ERole.ROLE_USER),
            "buyer2", user(TestTokens.OTHER_BUYER_ID, "buyer2", ERole.ROLE_USER),
            "seller", user(TestTokens.SELLER_ID, "seller", ERole.ROLE_SELLER),
            "seller2", user(TestTokens.OTHER_SELLER_ID, "seller2", ERole.ROLE_SELLER),
            "admin", user(TestTokens.ADMIN_ID, "admin", ERole.ROLE_ADMIN));

    @BeforeEach
    void stubUsers() {
        lenient().when(userRepository.findByUsername(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(users.get(inv.getArgument(0, String.class))));
        users.values().forEach(u -> {
            lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
            lenient().when(userRepository.existsById(u.getId())).thenReturn(true);
        });
    }

    protected static String bearer(String token) {
        return TestTokens.bearer(token);
    }

    protected static User user(String id, String username, ERole role) {
        User u = new User(username, username + "@wovely.test", "{noop}secret");
        u.setId(id);
        u.setRoles(Set.of(new Role(role)));
        u.setAccountStatus(EAccountStatus.ACTIVE);
        return u;
    }
}
