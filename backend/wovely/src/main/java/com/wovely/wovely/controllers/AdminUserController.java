package com.wovely.wovely.controllers;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.PenaltyRequest;
import com.wovely.wovely.payload.response.MessageResponse;
import com.wovely.wovely.payload.response.UserCrmDTO;
import com.wovely.wovely.repository.UserRepository;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    @Autowired
    UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<UserCrmDTO>> getAllUsers() {
        List<User> users = userRepository.findAll();
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

    @PostMapping("/{id}/penalty")
    public ResponseEntity<?> applyPenalty(@PathVariable("id") String id, @RequestBody PenaltyRequest request) {
        Optional<User> userData = userRepository.findById(id);

        if (userData.isPresent()) {
            User _user = userData.get();
            
            if ("STRIKE".equals(request.getAction())) {
                _user.setStrikes(_user.getStrikes() + 1);
            } else if ("STATUS".equals(request.getAction())) {
                _user.setAccountStatus(request.getNewStatus());
                if (request.getNewStatus() == EAccountStatus.SUSPENDED && request.getSuspendDays() != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DATE, request.getSuspendDays());
                    _user.setSuspendedUntil(cal.getTime());
                } else {
                    _user.setSuspendedUntil(null);
                }
            } else {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid action!"));
            }
            
            userRepository.save(_user);
            return new ResponseEntity<>(convertToDto(_user), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private UserCrmDTO convertToDto(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());
        return new UserCrmDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roles,
                user.getStrikes(),
                user.getAccountStatus(),
                user.getSuspendedUntil()
        );
    }
}
