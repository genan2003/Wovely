package com.wovely.wovely;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.repository.RoleRepository;

@SpringBootApplication
public class WovelyApplication {

	public static void main(String[] args) {
		SpringApplication.run(WovelyApplication.class, args);
	}

	@Bean
	CommandLineRunner initDatabase(RoleRepository roleRepository) {
		return args -> {
			if (!roleRepository.findByName(ERole.ROLE_USER).isPresent()) {
				roleRepository.save(new Role(ERole.ROLE_USER));
			}
			if (!roleRepository.findByName(ERole.ROLE_ADMIN).isPresent()) {
				roleRepository.save(new Role(ERole.ROLE_ADMIN));
			}
		};
	}
}
