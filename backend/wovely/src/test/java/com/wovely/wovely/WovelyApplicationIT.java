package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.TestTokens;

/** Smoke test for the core service test harness (Testcontainers Mongo + JWT helpers). */
class WovelyApplicationIT extends AbstractMongoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void seedsDemoUsersOnStartup() {
        assertThat(userRepository.existsByUsername("admin")).isTrue();
        assertThat(userRepository.existsByUsername("seller")).isTrue();
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
    }

    @Test
    void signinReturnsJwtWithRoles() throws Exception {
        mvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"seller\",\"password\":\"seller123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.username").value("seller"))
            .andExpect(jsonPath("$.roles", hasItem("ROLE_SELLER")));
    }

    @Test
    void signinWithWrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"seller\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void testTokenIsAcceptedForSeededAdmin() throws Exception {
        mvc.perform(get("/api/admin/users")
                .header("Authorization", TestTokens.bearer(TestTokens.admin())))
            .andExpect(status().isOk());
    }

    @Test
    void buyerTokenIsForbiddenOnAdminEndpoint() throws Exception {
        mvc.perform(get("/api/admin/users")
                .header("Authorization", TestTokens.bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
    }

    /** PLAN.md 4.5 — the OpenAPI description is served without a token and documents real endpoints and auth. */
    @org.junit.jupiter.api.Test
    void openApiSpecIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
            .andExpect(jsonPath("$.paths['/api/orders']").exists())
            .andExpect(jsonPath("$.paths['/api/wallet']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerJwt.scheme").value("bearer"))
            .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8090"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
