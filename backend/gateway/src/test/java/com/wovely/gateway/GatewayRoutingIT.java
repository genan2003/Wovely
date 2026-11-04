package com.wovely.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** PLAN.md 4.1 — routing, edge security, correlation ids and rate limiting against real Redis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingIT {

    static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=";

    static final MockWebServer CORE = start();
    static final MockWebServer PRODUCTS = start();
    static final MockWebServer INVENTORY = start();
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.1-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private static MockWebServer start() {
        MockWebServer s = new MockWebServer();
        try {
            s.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return s;
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("CORE_URL", () -> CORE.url("/").toString());
        r.add("PRODUCTS_URL", () -> PRODUCTS.url("/").toString());
        r.add("INVENTORY_URL", () -> INVENTORY.url("/").toString());
        r.add("JWT_SECRET", () -> SECRET);
        r.add("REDIS_HOST", REDIS::getHost);
        r.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
        r.add("AUTH_RATE_PER_SEC", () -> 1);
        r.add("AUTH_BURST", () -> 3);
    }

    @AfterAll
    static void stop() throws Exception {
        CORE.shutdown();
        PRODUCTS.shutdown();
        INVENTORY.shutdown();
        REDIS.stop();
    }

    @Autowired WebTestClient client;

    @BeforeEach
    void drain() throws Exception {
        for (MockWebServer s : List.of(CORE, PRODUCTS, INVENTORY)) {
            while (s.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
                // discard requests left over from earlier tests
            }
        }
    }

    static String token(String uid, long ttlMs) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.builder().setSubject("testuser").claim("uid", uid).claim("roles", List.of("ROLE_USER"))
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + ttlMs)).signWith(key).compact();
    }

    private static RecordedRequest next(MockWebServer s) throws InterruptedException {
        return s.takeRequest(2, TimeUnit.SECONDS);
    }

    @Test
    void routesByPathPrefix() throws Exception {
        PRODUCTS.enqueue(new MockResponse().setBody("[]").addHeader("Content-Type", "application/json"));
        client.get().uri("/api/products?deliveryEmission=ZERO").exchange().expectStatus().isOk();
        assertThat(next(PRODUCTS).getPath()).isEqualTo("/api/products?deliveryEmission=ZERO");

        PRODUCTS.enqueue(new MockResponse().setBody("{}"));
        client.get().uri("/api/reviews/product/p-1").exchange().expectStatus().isOk();
        assertThat(next(PRODUCTS).getPath()).isEqualTo("/api/reviews/product/p-1");

        INVENTORY.enqueue(new MockResponse().setBody("[]"));
        client.get().uri("/api/inventory/seller/s-1").exchange().expectStatus().isOk();
        assertThat(next(INVENTORY).getPath()).isEqualTo("/api/inventory/seller/s-1");

        CORE.enqueue(new MockResponse().setBody("[]"));
        client.get().uri("/api/orders/buyer/b-1").exchange().expectStatus().isOk();
        assertThat(next(CORE).getPath()).isEqualTo("/api/orders/buyer/b-1");
    }

    @Test
    void internalEndpointsAreNotReachableFromOutside() throws Exception {
        client.post().uri("/internal/products/seller/s/product/p/reduce-stock")
            .header(EdgeFilter.INTERNAL_KEY_HEADER, "guess")
            .exchange().expectStatus().isNotFound()
            .expectBody().jsonPath("$.status").isEqualTo(404);
        assertThat(next(PRODUCTS)).isNull();
        assertThat(next(CORE)).isNull();
    }

    @Test
    void clientSentInternalKeyIsStripped() throws Exception {
        CORE.enqueue(new MockResponse().setBody("{}"));
        client.get().uri("/api/wallet").header(EdgeFilter.INTERNAL_KEY_HEADER, "stolen").exchange().expectStatus().isOk();
        assertThat(next(CORE).getHeader(EdgeFilter.INTERNAL_KEY_HEADER)).isNull();
    }

    @Test
    void invalidOrExpiredTokensStopAtTheEdge() throws Exception {
        for (String bad : new String[] { "garbage", token("u-1", -1000) }) {
            client.get().uri("/api/wallet").header("Authorization", "Bearer " + bad).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid or expired token");
        }
        assertThat(next(CORE)).isNull();

        CORE.enqueue(new MockResponse().setBody("{}"));
        String good = token("u-1", 60_000);
        client.get().uri("/api/wallet").header("Authorization", "Bearer " + good).exchange().expectStatus().isOk();
        assertThat(next(CORE).getHeader("Authorization")).isEqualTo("Bearer " + good);
    }

    @Test
    void correlationIdIsCreatedOrReusedAndForwarded() throws Exception {
        CORE.enqueue(new MockResponse().setBody("{}"));
        String created = client.get().uri("/api/wallet").exchange().expectStatus().isOk()
            .returnResult(String.class).getResponseHeaders().getFirst(EdgeFilter.CORRELATION_HEADER);
        assertThat(created).matches("[0-9a-f-]{36}");
        assertThat(next(CORE).getHeader(EdgeFilter.CORRELATION_HEADER)).isEqualTo(created);

        CORE.enqueue(new MockResponse().setBody("{}"));
        client.get().uri("/api/wallet").header(EdgeFilter.CORRELATION_HEADER, "client-abc-123").exchange()
            .expectHeader().valueEquals(EdgeFilter.CORRELATION_HEADER, "client-abc-123");
        assertThat(next(CORE).getHeader(EdgeFilter.CORRELATION_HEADER)).isEqualTo("client-abc-123");

        CORE.enqueue(new MockResponse().setBody("{}"));
        String replaced = client.get().uri("/api/wallet").header(EdgeFilter.CORRELATION_HEADER, "bad id; drop <script>").exchange()
            .returnResult(String.class).getResponseHeaders().getFirst(EdgeFilter.CORRELATION_HEADER);
        assertThat(replaced).matches("[0-9a-f-]{36}");
        next(CORE);
    }

    @Test
    void signInIsRateLimitedPerClient() throws Exception {
        int ok = 0, limited = 0;
        for (int i = 0; i < 8; i++) {
            CORE.enqueue(new MockResponse().setBody("{}"));
            int status = client.post().uri("/api/auth/signin").header("X-Real-IP", "203.0.113.7")
                .bodyValue("{}").exchange().returnResult(String.class).getStatus().value();
            if (status == 200) ok++;
            if (status == 429) limited++;
        }
        assertThat(ok).isBetween(3, 4);  // burst 3 (+1 if a token refilled meanwhile)
        assertThat(limited).isGreaterThanOrEqualTo(4);

        CORE.enqueue(new MockResponse().setBody("{}"));
        client.post().uri("/api/auth/signin").header("X-Real-IP", "198.51.100.9").bodyValue("{}")
            .exchange().expectStatus().isOk(); // a different client has its own bucket
    }
}
