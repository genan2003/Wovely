package com.wovely.wovely.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.services.AuditService;
import com.wovely.wovely.services.RefreshTokenService;
import com.wovely.wovely.services.UserQueryService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 3.1 — server-side user search for the admin CRM. */
@WebMvcTest(AdminUserController.class)
class AdminUserSearchTest extends SecuredWebMvcTest {

    @MockitoBean UserQueryService userQueryService;
    @MockitoBean AuditService auditService;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean com.wovely.wovely.services.UserPenaltyService penaltyService;

    @Test
    void adminSearchPassesQueryAndStatusToService() throws Exception {
        when(userQueryService.search("ali", EAccountStatus.BANNED)).thenReturn(List.of(users.get("buyer2")));
        mvc.perform(get("/api/admin/users").param("query", "ali").param("status", "BANNED")
                .header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("buyer2"))
            .andExpect(jsonPath("$[0].email").value("buyer2@wovely.test"))
            .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void noParamsListsEveryone() throws Exception {
        when(userQueryService.search(null, null)).thenReturn(List.copyOf(users.values()));
        mvc.perform(get("/api/admin/users").header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void unknownStatusIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/users").param("status", "NOPE").header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isBadRequest());
        verify(userQueryService, never()).search(any(), any());
    }

    @Test
    void nonAdminsCannotSearch() throws Exception {
        mvc.perform(get("/api/admin/users").param("query", "a")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").param("query", "a").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").param("query", "a").header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
        verify(userQueryService, never()).search(any(), any());
    }
}
