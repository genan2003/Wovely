package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 1.6 — no sender spoofing, no reading other people's conversations. */
@WebMvcTest(ChatController.class)
class ChatControllerTest extends SecuredWebMvcTest {

    @MockitoBean ChatMessageRepository chatMessageRepository;
    @MockitoBean com.wovely.wovely.services.ChatQueryService chatQueryService;
    @MockitoBean com.wovely.wovely.realtime.ChatNotifier chatNotifier;

    @Test
    void senderIsTakenFromTokenNotBody() throws Exception {
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/chat/send").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senderId\":\"" + TestTokens.OTHER_BUYER_ID + "\",\"receiverId\":\"" + TestTokens.SELLER_ID
                        + "\",\"content\":\"Hi!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.senderId").value(TestTokens.BUYER_ID));
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getSenderId()).isEqualTo(TestTokens.BUYER_ID);
        verify(chatNotifier).messageSent(saved.getValue()); // PLAN.md 3.4: pushed in real time
    }

    @Test
    void messageToUnknownUserOrEmptyContentIsBadRequest() throws Exception {
        mvc.perform(post("/api/chat/send").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"ghost\",\"content\":\"Hi\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/send").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + TestTokens.SELLER_ID + "\",\"content\":\" \"}"))
            .andExpect(status().isBadRequest());
        verify(chatMessageRepository, never()).save(any());
        verify(chatNotifier, never()).messageSent(any());
    }

    @Test
    void cannotReadSomeoneElsesConversation() throws Exception {
        mvc.perform(get("/api/chat/conversation").header(AUTH, bearer(TestTokens.otherBuyer()))
                .param("user1", TestTokens.BUYER_ID).param("user2", TestTokens.SELLER_ID))
            .andExpect(status().isForbidden());
        verify(chatMessageRepository, never()).findConversationLogs(any(), any());
    }

    @Test
    void canReadOwnConversation() throws Exception {
        when(chatMessageRepository.findConversationLogs(TestTokens.BUYER_ID, TestTokens.SELLER_ID)).thenReturn(List.of());
        mvc.perform(get("/api/chat/conversation").header(AUTH, bearer(TestTokens.buyer()))
                .param("user1", TestTokens.BUYER_ID).param("user2", TestTokens.SELLER_ID))
            .andExpect(status().isOk());
    }

    @Test
    void unreadAndConversationListAreSelfOnly() throws Exception {
        mvc.perform(get("/api/chat/unread/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/conversations/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/unread/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk());
    }

    @Test
    void adminCannotBrowseChatsDirectly() throws Exception {
        mvc.perform(get("/api/chat/conversations/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isForbidden());
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/chat/unread/" + TestTokens.BUYER_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void messagesAreValidatedBeforeAnythingIsSaved() throws Exception {
        String longText = "a".repeat(2001);
        for (String body : new String[] {
                "{\"receiverId\":\"" + TestTokens.SELLER_ID + "\",\"content\":\"" + longText + "\"}",
                "{\"receiverId\":\"" + TestTokens.SELLER_ID + "\",\"content\":\"   \"}",
                "{\"content\":\"Hi\"}" }) {
            mvc.perform(post("/api/chat/send").header(AUTH, bearer(TestTokens.buyer()))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
