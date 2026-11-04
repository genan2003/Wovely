package com.wovely.wovely.controllers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.LoginRequest;
import com.wovely.wovely.payload.request.SignupRequest;
import com.wovely.wovely.payload.response.JwtResponse;
import com.wovely.wovely.payload.response.MessageResponse;
import com.wovely.wovely.repository.RoleRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.jwt.JwtUtils;
import com.wovely.wovely.security.services.UserDetailsImpl;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    com.wovely.wovely.services.RefreshTokenService refreshTokenService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                roles).withRefresh(refreshTokenService.issue(userDetails.getId()), jwtUtils.getJwtExpirationMs()));
    }

    /**
     * Exchange a refresh token for a new access token + rotated refresh token (PLAN.md 2.9).
     * Banned/suspended users are refused and signed out everywhere.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody java.util.Map<String, String> body) {
        var rotation = refreshTokenService.rotate(body.get("refreshToken"))
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token"));
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token"));
        UserDetailsImpl details = UserDetailsImpl.build(user);
        if (!details.isAccountNonLocked()) {
            refreshTokenService.revokeAll(user.getId());
            throw new org.springframework.security.authentication.LockedException("Account is locked");
        }
        List<String> roles = details.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList());
        return ResponseEntity.ok(new JwtResponse(jwtUtils.generateJwtToken(details), details.getId(),
                details.getUsername(), details.getEmail(), roles)
                .withRefresh(rotation.newToken(), jwtUtils.getJwtExpirationMs()));
    }

    /** Revokes the given refresh token. Always 200 (idempotent). */
    @PostMapping("/signout")
    public ResponseEntity<?> signout(@RequestBody(required = false) java.util.Map<String, String> body) {
        refreshTokenService.revoke(body != null ? body.get("refreshToken") : null);
        return ResponseEntity.ok(new MessageResponse("Signed out"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRoles();
        if (strRoles != null && strRoles.stream().anyMatch(r -> "admin".equalsIgnoreCase(r))) {
            // Admins are created by the seeder or by another admin, never via public signup (PLAN.md 1.1)
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Role 'admin' cannot be self-assigned."));
        }
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "seller":
                        Role sellerRole = roleRepository.findByName(ERole.ROLE_SELLER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(sellerRole);
                        break;
                    default:
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
        }

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
