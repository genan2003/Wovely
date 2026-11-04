package com.wovely.wovely.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.ERole;
import com.wovely.wovely.models.Role;
import com.wovely.wovely.models.User;
import com.wovely.wovely.security.jwt.JwtUtils;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.security.services.UserDetailsServiceImpl;

/** PLAN.md 3.4 — STOMP sessions are authenticated and confined to the caller's own queues. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompAuthInterceptorTest {

    @Mock JwtUtils jwt;
    @Mock UserDetailsServiceImpl users;
    StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthInterceptor(jwt, users);
        when(jwt.validateJwtToken("good")).thenReturn(true);
        when(jwt.validateJwtToken("banned")).thenReturn(true);
        when(jwt.getUserNameFromJwtToken("good")).thenReturn("ana");
        when(jwt.getUserNameFromJwtToken("banned")).thenReturn("bob");
        when(users.loadUserByUsername("ana")).thenReturn(user("u-ana", "ana", EAccountStatus.ACTIVE));
        when(users.loadUserByUsername("bob")).thenReturn(user("u-bob", "bob", EAccountStatus.BANNED));
    }

    private static UserDetailsImpl user(String id, String name, EAccountStatus status) {
        User u = new User(name, name + "@x.test", "pw");
        u.setId(id);
        u.setRoles(Set.of(new Role(ERole.ROLE_USER)));
        u.setAccountStatus(status);
        return UserDetailsImpl.build(u);
    }

    private static Message<byte[]> frame(StompCommand cmd, String authorization, String destination,
            java.security.Principal user) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        if (authorization != null) acc.addNativeHeader("Authorization", authorization);
        if (destination != null) acc.setDestination(destination);
        if (user != null) acc.setUser(user);
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void connectWithValidTokenBindsTheUserId() {
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null);
        assertThat(StompHeaderAccessor.wrap(out).getUser()).isNotNull()
            .extracting(java.security.Principal::getName).isEqualTo("u-ana");
    }

    @Test
    void connectWithoutBadOrBannedTokenIsRejected() {
        when(jwt.validateJwtToken("expired")).thenReturn(false);
        for (String header : new String[] { null, "good", "Bearer expired", "Bearer banned" }) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, header, null, null), null))
                .as(String.valueOf(header)).isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void subscribeOnlyToOwnUserQueues() {
        var me = new StompAuthInterceptor.UserIdPrincipal("u-ana");
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/messages", me), null);
        for (String dest : new String[] { "/queue/messages", "/topic/all", "/user/u-bob/queue/messages" }) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, dest, me), null))
                .as(dest).isInstanceOf(MessageDeliveryException.class);
        }
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/messages", null), null))
            .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void sendingOverTheSocketIsNotAllowed() {
        var me = new StompAuthInterceptor.UserIdPrincipal("u-ana");
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/chat", me), null))
            .isInstanceOf(MessageDeliveryException.class);
    }
}
