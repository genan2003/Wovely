package com.wovely.wovely.realtime;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.wovely.wovely.security.jwt.JwtUtils;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.security.services.UserDetailsServiceImpl;

/**
 * Authenticates STOMP sessions (PLAN.md 3.4). The JWT travels in the CONNECT frame's
 * {@code Authorization} header — never in the URL — and is checked like on REST calls (signature,
 * expiry, account not banned/suspended). Clients may only subscribe to their own user queues and
 * may not SEND: messages are posted over REST, where validation and ownership live.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    /** Session principal: the user id, so {@code convertAndSendToUser(userId, …)} routes by id. */
    public record UserIdPrincipal(String getName) implements Principal {
    }

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl users;

    public StompAuthInterceptor(JwtUtils jwtUtils, UserDetailsServiceImpl users) {
        this.jwtUtils = jwtUtils;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return message;
        }
        switch (acc.getCommand()) {
            case CONNECT -> acc.setUser(authenticate(acc.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> {
                String dest = acc.getDestination();
                if (acc.getUser() == null || dest == null || !dest.startsWith("/user/queue/")) {
                    throw new MessageDeliveryException("Only your own /user/queue/* destinations can be subscribed");
                }
            }
            case SEND -> throw new MessageDeliveryException("Send messages with POST /api/chat/send");
            default -> {
            }
        }
        return message;
    }

    Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        String token = header.substring(7);
        if (!jwtUtils.validateJwtToken(token)) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
        UserDetailsImpl user = (UserDetailsImpl) users.loadUserByUsername(jwtUtils.getUserNameFromJwtToken(token));
        if (!user.isAccountNonLocked() || !user.isEnabled()) {
            throw new MessageDeliveryException("Account is not active");
        }
        return new UserIdPrincipal(user.getId());
    }

}
