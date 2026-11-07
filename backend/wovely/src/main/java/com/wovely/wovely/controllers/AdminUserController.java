package com.wovely.wovely.controllers;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.PenaltyRequest;
import com.wovely.wovely.payload.response.UserCrmDTO;
import com.wovely.wovely.repository.UserRepository;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    @Autowired
    UserRepository userRepository;

    @Autowired
    com.wovely.wovely.services.RefreshTokenService refreshTokenService;

    @Autowired
    com.wovely.wovely.services.AuditService auditService;

    @Autowired
    com.wovely.wovely.services.UserQueryService userQueryService;

    @Autowired
    com.wovely.wovely.services.UserPenaltyService penaltyService;

    /** {@code ?query=} matches username/email/full name (case-insensitive), {@code ?status=} filters (PLAN.md 3.1). */
    @GetMapping
    public ResponseEntity<List<UserCrmDTO>> getAllUsers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) EAccountStatus status) {
        List<User> users = userQueryService.search(query, status);
        List<UserCrmDTO> dtos = users.stream().map(this::convertToDto).collect(Collectors.toList());
        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserCrmDTO> getUserById(@PathVariable("id") String id) {
        Optional<User> userData = userRepository.findById(id);
        if (userData.isPresent()) {
            return new ResponseEntity<>(convertToDto(userData.get()), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /** STRIKE, or STATUS with {@code newStatus} (SUSPENDED needs {@code suspendDays} 1–365). */
    @PostMapping("/{id}/penalty")
    public UserCrmDTO applyPenalty(@PathVariable("id") String id, @RequestBody(required = false) PenaltyRequest request) {
        return convertToDto(penaltyService.apply(id, request));
    }

    private UserCrmDTO convertToDto(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());
        return new UserCrmDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                roles,
                user.getStrikes(),
                user.effectiveStatus(new java.util.Date()),
                user.effectiveStatus(new java.util.Date()) == EAccountStatus.SUSPENDED ? user.getSuspendedUntil() : null
        );
    }
}
