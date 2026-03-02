package com.wovely.wovely.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;

public interface RoleRepository extends MongoRepository<Role, String> {
    Optional<Role> findByName(ERole name);
}
