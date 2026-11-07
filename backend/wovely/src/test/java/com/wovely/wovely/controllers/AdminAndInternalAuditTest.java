package com.wovely.wovely.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.User;
import com.wovely.wovely.services.AuditService;
import com.wovely.wovely.services.RefreshTokenService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 2.7 (penalties audited, moderation audit endpoint) and 2.9 (ban revokes refresh tokens). */
@WebMvcTest({ AdminUserController.class, InternalAuditController.class })
@org.springframework.context.annotation.Import(com.wovely.wovely.services.UserPenaltyService.class)
class AdminAndInternalAuditTest extends SecuredWebMvcTest {

    @MockitoBean AuditService auditService;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean com.wovely.wovely.services.UserQueryService userQueryService;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Test
    void banRevokesRefreshTokensAndIsAudited() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STATUS\",\"newStatus\":\"BANNED\"}"))
            .andExpect(status().isOk());
        verify(refreshTokenService).revokeAll(TestTokens.OTHER_BUYER_ID);
        verify(auditService).record(eq("USER_PENALTY"), eq(TestTokens.OTHER_BUYER_ID), contains("status=BANNED"));
    }

    @Test
    void strikeIsAuditedButDoesNotSignOut() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STRIKE\"}"))
            .andExpect(status().isOk());
        verify(refreshTokenService, never()).revokeAll(any());
        verify(auditService).record(eq("USER_PENALTY"), eq(TestTokens.OTHER_BUYER_ID), contains("strikes=1"));
    }

    @Test
    void statusPenaltyWithoutNewStatusIsBadRequest() throws Exception {
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STATUS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void suspensionNeedsALengthOfOneTo365Days() throws Exception {
        for (String days : new String[] { "", ",\"suspendDays\":0", ",\"suspendDays\":366", ",\"suspendDays\":-3" }) {
            mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STATUS\",\"newStatus\":\"SUSPENDED\"" + days + "}"))
                .andExpect(status().isBadRequest());
        }
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void suspensionHasAnEndAndSignsTheUserOut() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STATUS\",\"newStatus\":\"SUSPENDED\",\"suspendDays\":14}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
            .andExpect(jsonPath("$.suspendedUntil").exists());
        verify(refreshTokenService).revokeAll(TestTokens.OTHER_BUYER_ID);
        verify(auditService).record(eq("USER_PENALTY"), eq(TestTokens.OTHER_BUYER_ID), contains("until"));
    }

    @Test
    void unknownActionAndUnknownUser() throws Exception {
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SMITE\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users/nobody/penalty").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STRIKE\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/users/" + TestTokens.OTHER_BUYER_ID + "/penalty").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"STRIKE\"}"))
            .andExpect(status().isForbidden());
    }

    static final String EVENT = "{\"actorId\":\"admin-1\",\"actorUsername\":\"admin\",\"actionType\":\"PRODUCT_STATUS\",\"targetId\":\"p-1\",\"details\":\"PENDING -> APPROVED\"}";

    @Test
    void internalAuditRequiresServiceKey() throws Exception {
        mvc.perform(post("/internal/audit").contentType(MediaType.APPLICATION_JSON).content(EVENT))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/audit").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content(EVENT))
            .andExpect(status().isForbidden());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void internalAuditWithKeyIsRecorded() throws Exception {
        mvc.perform(post("/internal/audit").header("X-Internal-Api-Key", "test-internal-key")
                .contentType(MediaType.APPLICATION_JSON).content(EVENT))
            .andExpect(status().isAccepted());
        verify(auditService).record("admin-1", "admin", "PRODUCT_STATUS", "p-1", "PENDING -> APPROVED");
    }
}
