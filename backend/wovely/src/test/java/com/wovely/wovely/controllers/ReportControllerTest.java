package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.repository.ReportRepository;
import com.wovely.wovely.services.AuditService;
import com.wovely.wovely.services.ReportService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 3.2 — validated reports; admins read a chat only through a report about it. */
@WebMvcTest(ReportController.class)
@Import(ReportService.class)
class ReportControllerTest extends SecuredWebMvcTest {

    @MockitoBean ReportRepository reportRepository;
    @MockitoBean ChatMessageRepository chatMessageRepository;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean AuditService auditService;

    static final String SORTED_PAIR = TestTokens.BUYER_ID + ":" + TestTokens.SELLER_ID;

    @BeforeEach
    void stubs() {
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private org.springframework.test.web.servlet.ResultActions report(String token, String json) throws Exception {
        return mvc.perform(post("/api/reports").header(AUTH, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------------------------------------------------------------- create: CHAT

    @Test
    void chatReportByOtherUserIdStoresSortedParticipants() throws Exception {
        when(chatMessageRepository.conversationExists(TestTokens.SELLER_ID, TestTokens.BUYER_ID)).thenReturn(true);
        report(TestTokens.seller(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.BUYER_ID
                + "\",\"reason\":\"  rude  \",\"targetName\":\"forged\",\"status\":\"RESOLVED\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reporterId").value(TestTokens.SELLER_ID))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.targetId").value(SORTED_PAIR))
            .andExpect(jsonPath("$.participantIds[0]").value(TestTokens.BUYER_ID))
            .andExpect(jsonPath("$.participantIds[1]").value(TestTokens.SELLER_ID))
            .andExpect(jsonPath("$.targetName").value("Chat with testuser"))
            .andExpect(jsonPath("$.reason").value("rude"));
    }

    @Test
    void legacyCompositeTargetIdStillWorks() throws Exception {
        when(chatMessageRepository.conversationExists(TestTokens.BUYER_ID, TestTokens.SELLER_ID)).thenReturn(true);
        report(TestTokens.buyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.SELLER_ID + ":" + TestTokens.BUYER_ID
                + "\",\"reason\":\"spam\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetId").value(SORTED_PAIR));
    }

    @Test
    void cannotReportSomeoneElsesConversation() throws Exception {
        report(TestTokens.otherBuyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + SORTED_PAIR + "\",\"reason\":\"snoop\"}")
            .andExpect(status().isForbidden());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void cannotReportAConversationThatDoesNotExist() throws Exception {
        when(chatMessageRepository.conversationExists(TestTokens.OTHER_BUYER_ID, TestTokens.SELLER_ID)).thenReturn(false);
        report(TestTokens.otherBuyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.SELLER_ID + "\",\"reason\":\"x\"}")
            .andExpect(status().isBadRequest());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void chatWithUnknownUserIs404AndSelfIs400() throws Exception {
        report(TestTokens.buyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"ghost\",\"reason\":\"x\"}")
            .andExpect(status().isNotFound());
        report(TestTokens.buyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.BUYER_ID + "\",\"reason\":\"x\"}")
            .andExpect(status().isBadRequest());
        report(TestTokens.buyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"a:b:c\",\"reason\":\"x\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateOpenReportIsConflict() throws Exception {
        when(chatMessageRepository.conversationExists(TestTokens.BUYER_ID, TestTokens.SELLER_ID)).thenReturn(true);
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(TestTokens.BUYER_ID, "CHAT", SORTED_PAIR, "PENDING"))
            .thenReturn(true);
        report(TestTokens.buyer(), "{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.SELLER_ID + "\",\"reason\":\"again\"}")
            .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- create: PRODUCT & validation

    @Test
    void productReportTakesNameFromCatalog() throws Exception {
        when(catalogClient.getProduct("p-1")).thenReturn(
            new CatalogClient.ProductSnapshot("p-1", "Willow basket", 10.0, TestTokens.SELLER_ID, "img", "APPROVED", 3));
        report(TestTokens.buyer(), "{\"targetType\":\"PRODUCT\",\"targetId\":\"p-1\",\"targetName\":\"x\",\"reason\":\"counterfeit\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetName").value("Willow basket"))
            .andExpect(jsonPath("$.participantIds").doesNotExist());
    }

    @Test
    void unknownProductIsRejected() throws Exception {
        when(catalogClient.getProduct("nope")).thenThrow(ApiException.badRequest("Product not found: nope"));
        report(TestTokens.buyer(), "{\"targetType\":\"PRODUCT\",\"targetId\":\"nope\",\"reason\":\"x\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPayloadsAreBadRequest() throws Exception {
        report(TestTokens.buyer(), "{\"targetType\":\"USER\",\"targetId\":\"x\",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        report(TestTokens.buyer(), "{\"targetType\":\"PRODUCT\",\"targetId\":\"p-1\"}").andExpect(status().isBadRequest());
        report(TestTokens.buyer(), "{\"targetType\":\"PRODUCT\",\"targetId\":\"\",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        report(TestTokens.buyer(), "{\"targetType\":\"PRODUCT\",\"targetId\":\"p-1\",\"reason\":\"" + "x".repeat(1001) + "\"}")
            .andExpect(status().isBadRequest());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void anonymousCannotReport() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"PRODUCT\",\"targetId\":\"p-1\",\"reason\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- decide

    private Report pendingChatReport() {
        Report r = new Report(TestTokens.BUYER_ID, "CHAT", SORTED_PAIR, "spam");
        r.setId("r-1");
        r.setParticipantIds(List.of(TestTokens.BUYER_ID, TestTokens.SELLER_ID));
        return r;
    }

    @Test
    void adminResolvesPendingReportAndItIsAudited() throws Exception {
        when(reportRepository.findById("r-1")).thenReturn(Optional.of(pendingChatReport()));
        mvc.perform(patch("/api/reports/r-1/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISMISSED"));
        verify(auditService).record(eq("REPORT_DISMISSED"), eq("r-1"), anyString());
    }

    @Test
    void decidingTwiceIsConflictAndBadStatusIs400() throws Exception {
        Report done = pendingChatReport();
        done.setStatus("RESOLVED");
        when(reportRepository.findById("r-1")).thenReturn(Optional.of(done));
        mvc.perform(patch("/api/reports/r-1/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
            .andExpect(status().isConflict());
        for (String bad : new String[] { "PENDING", "DELETED", "" }) {
            mvc.perform(patch("/api/reports/r-1/status").header(AUTH, bearer(TestTokens.admin()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + bad + "\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/reports/missing/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsDecide() throws Exception {
        mvc.perform(patch("/api/reports/r-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- chat log

    @Test
    void chatLogReturnsOnlyTheReportedConversationOldestFirstAndIsAudited() throws Exception {
        when(reportRepository.findById("r-1")).thenReturn(Optional.of(pendingChatReport()));
        ChatMessage newer = new ChatMessage(TestTokens.SELLER_ID, TestTokens.BUYER_ID, null, null, "second");
        newer.setTimestamp(LocalDateTime.now());
        ChatMessage older = new ChatMessage(TestTokens.BUYER_ID, TestTokens.SELLER_ID, null, null, "first");
        older.setTimestamp(LocalDateTime.now().minusHours(1));
        when(chatMessageRepository.findConversationLogs(TestTokens.BUYER_ID, TestTokens.SELLER_ID)).thenReturn(List.of(newer, older));

        mvc.perform(get("/api/reports/chat-log/r-1").header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].content").value("first"))
            .andExpect(jsonPath("$[1].content").value("second"));
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("CHAT_LOG_VIEWED"), eq("r-1"), details.capture());
        assertThat(details.getValue()).contains(SORTED_PAIR);
    }

    @Test
    void chatLogNeedsAChatReport() throws Exception {
        Report product = new Report(TestTokens.BUYER_ID, "PRODUCT", "p-1", "x");
        when(reportRepository.findById("r-2")).thenReturn(Optional.of(product));
        mvc.perform(get("/api/reports/chat-log/r-2").header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/chat-log/none").header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/chat-log/r-1").header(AUTH, bearer(TestTokens.seller()))).andExpect(status().isForbidden());
        verify(chatMessageRepository, never()).findConversationLogs(any(), any());
    }
}
