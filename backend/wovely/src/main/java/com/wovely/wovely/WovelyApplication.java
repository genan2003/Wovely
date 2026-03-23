package com.wovely.wovely;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.RoleRepository;
import com.wovely.wovely.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
public class WovelyApplication {

	public static void main(String[] args) {
		SpringApplication.run(WovelyApplication.class, args);
	}

	@Bean
	CommandLineRunner initDatabase(RoleRepository roleRepository, UserRepository userRepository,
			PasswordEncoder encoder) {
		return args -> {
			// Initialize roles
			if (!roleRepository.findByName(ERole.ROLE_USER).isPresent()) {
				roleRepository.save(new Role(ERole.ROLE_USER));
			}
			if (!roleRepository.findByName(ERole.ROLE_ADMIN).isPresent()) {
				roleRepository.save(new Role(ERole.ROLE_ADMIN));
			}

			// Create admin user if not exists
			if (!userRepository.existsByUsername("admin")) {
				User admin = new User("admin", "admin@wovely.com", encoder.encode("admin123"));
				Set<Role> roles = new HashSet<>();
				Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN).orElseThrow();
				roles.add(adminRole);
				admin.setRoles(roles);
				admin.setAccountStatus(EAccountStatus.ACTIVE);
				userRepository.save(admin);
				System.out.println("✅ Admin user created: username='admin', password='admin123'");
			}

			// Create test regular user
			if (!userRepository.existsByUsername("testuser")) {
				User user = new User("testuser", "user@wovely.com", encoder.encode("user123"));
				Set<Role> roles = new HashSet<>();
				Role userRole = roleRepository.findByName(ERole.ROLE_USER).orElseThrow();
				roles.add(userRole);
				user.setRoles(roles);
				user.setAccountStatus(EAccountStatus.ACTIVE);
				userRepository.save(user);
				System.out.println("✅ Test user created: username='testuser', password='user123'");
			}
		};
	}
}
