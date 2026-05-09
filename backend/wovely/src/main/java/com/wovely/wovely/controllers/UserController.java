package com.wovely.wovely.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    UserRepository userRepository;

    @GetMapping("/profile/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> getUserProfile(@PathVariable String id) {
        return userRepository.findById(id)
            .map(user -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("id", user.getId());
                profile.put("username", user.getUsername());
                profile.put("fullName", user.getFullName());
                profile.put("email", user.getEmail());
                profile.put("makerStory", user.getMakerStory());
                profile.put("workshopImageUrl", user.getWorkshopImageUrl());
                profile.put("city", user.getCity());
                profile.put("region", user.getRegion());
                // Add more fields as needed (address, payment info etc. would go here in a real app)
                return ResponseEntity.ok(profile);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> updateProfile(@PathVariable String id, @RequestBody Map<String, String> data) {
        Optional<User> userOpt = userRepository.findById(id);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            if (data.containsKey("fullName")) user.setFullName(data.get("fullName"));
            if (data.containsKey("makerStory")) user.setMakerStory(data.get("makerStory"));
            if (data.containsKey("city")) user.setCity(data.get("city"));
            if (data.containsKey("region")) user.setRegion(data.get("region"));
            if (data.containsKey("workshopImageUrl")) user.setWorkshopImageUrl(data.get("workshopImageUrl"));
            
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
