package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.RoleRepository;
import com.wovely.wovely.support.SecuredWebMvcTest;

/** PLAN.md 1.1 — public signup must never grant ROLE_ADMIN. */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends SecuredWebMvcTest {

    @MockitoBean
    RoleRepository roleRepository;

    @BeforeEach
    void roles() {
        for (ERole r : ERole.values()) {
            when(roleRepository.findByName(r)).thenReturn(Optional.of(new Role(r)));
        }
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
    }

    private static String signup(String rolesJson) {
        return "{\"username\":\"newbie\",\"email\":\"newbie@wovely.test\",\"password\":\"secret123\""
                + (rolesJson != null ? ",\"roles\":" + rolesJson : "") + "}";
    }

    @Test
    void signupWithAdminRoleIsRejected() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup("[\"admin\"]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Error: Role 'admin' cannot be self-assigned."));
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupWithAdminHiddenAmongOtherRolesIsRejected() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup("[\"seller\",\"ADMIN\"]")))
            .andExpect(status().isBadRequest());
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupAsSellerGrantsSellerRole() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup("[\"seller\"]")))
            .andExpect(status().isOk());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly(ERole.ROLE_SELLER);
    }

    @Test
    void signupWithoutRolesGrantsUserRole() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup(null)))
            .andExpect(status().isOk());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly(ERole.ROLE_USER);
    }

    @Test
    void signupValidationErrorsAreBadRequest() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"email\":\"nope\",\"password\":\"1\"}"))
            .andExpect(status().isBadRequest());
    }
}
