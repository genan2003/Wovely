package com.wovely.wovely.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.RoleRepository;
import com.wovely.wovely.repository.UserRepository;

/**
 * Always ensures the three roles exist. Demo accounts (admin/seller/testuser) are created
 * only with the {@code dev} profile — never in production. Passwords are never logged.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public DataSeeder(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder encoder,
            Environment environment) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ERole role : ERole.values()) {
            if (roleRepository.findByName(role).isEmpty()) {
                roleRepository.save(new Role(role));
            }
        }
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            seedUser("admin", "admin@wovely.com", "admin123", ERole.ROLE_ADMIN);
            seedUser("seller", "seller@wovely.com", "seller123", ERole.ROLE_SELLER);
            seedUser("testuser", "user@wovely.com", "user123", ERole.ROLE_USER);
        }
    }

    private void seedUser(String username, String email, String password, ERole roleName) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User user = new User(username, email, encoder.encode(password));
        user.setRoles(Set.of(roleRepository.findByName(roleName).orElseThrow()));
        user.setAccountStatus(EAccountStatus.ACTIVE);
        userRepository.save(user);
        logger.info("Dev profile: seeded demo user '{}' ({})", username, roleName);
    }
}
