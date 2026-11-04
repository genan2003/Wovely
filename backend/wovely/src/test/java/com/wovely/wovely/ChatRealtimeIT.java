package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 3.4 — a message posted over REST reaches the receiver's STOMP session in real time. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatRealtimeIT extends AbstractMongoIT {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    WebSocketStompClient client;
    User seller;
    User buyer;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        seller = users.findByUsername("seller").orElseThrow();   // seeded by the dev profile
        buyer = users.findByUsername("testuser").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    private String tokenFor(User u, String role) {
        return TestTokens.token(u.getUsername(), u.getId(), role);
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (token != null) connect.add("Authorization", "Bearer " + token);
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin("http://localhost:4200");
        return client.connectAsync("ws://localhost:" + port + "/ws", http, connect, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void receiverGetsTheMessageWithoutPolling() throws Exception {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        StompSession session = connect(tokenFor(seller, "ROLE_SELLER"));
        session.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders headers, Object payload) { inbox.add((Map<String, Object>) payload); }
        });
        Thread.sleep(300); // let SUBSCRIBE reach the broker

        mvc.perform(post("/api/chat/send").header("Authorization", "Bearer " + tokenFor(buyer, "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + seller.getId() + "\",\"content\":\"Is the basket still available?\"}"))
            .andExpect(status().isCreated());

        Map<String, Object> pushed = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(pushed.get("content")).isEqualTo("Is the basket still available?");
        assertThat(pushed.get("senderId")).isEqualTo(buyer.getId());
        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRefused() throws Exception {
        StompSession session;
        try {
            session = connect(null);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException refused) {
            return; // handshake/CONNECT refused outright
        }
        // Otherwise the server answers CONNECT with an ERROR frame and closes the socket.
        long deadline = System.currentTimeMillis() + 3000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).as("session without a token must be closed").isFalse();
    }
}
