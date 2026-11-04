package com.wovely.products.support;

import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Mints JWTs in the same shape the core service issues (subject = username, {@code uid}, {@code roles}),
 * signed with the test secret from {@code application-test.properties}.
 *
 * <p>Use as {@code header("Authorization", TestTokens.bearer(TestTokens.seller()))}.
 */
public final class TestTokens {

    /** Must match {@code wovely.app.jwtSecret} / {@code jwt.secret} in application-test.properties. */
    public static final String SECRET = "ujNbPAGunYr5/ls4M6TLvPAQYzXttge7ACMk1KYrPn+HvoDk+l6QN/EIzPHNSzUYvrdgcDpYk/3t5tQmuoMKdQ==";

    private TestTokens() {
    }

    public static final String BUYER_ID = "buyer-1";
    public static final String OTHER_BUYER_ID = "buyer-2";
    public static final String SELLER_ID = "seller-1";
    public static final String OTHER_SELLER_ID = "seller-2";
    public static final String ADMIN_ID = "admin-1";

    public static String buyer() {
        return token("testuser", BUYER_ID, "ROLE_USER");
    }

    public static String otherBuyer() {
        return token("buyer2", OTHER_BUYER_ID, "ROLE_USER");
    }

    public static String seller() {
        return token("seller", SELLER_ID, "ROLE_SELLER");
    }

    public static String otherSeller() {
        return token("seller2", OTHER_SELLER_ID, "ROLE_SELLER");
    }

    public static String admin() {
        return token("admin", ADMIN_ID, "ROLE_ADMIN");
    }

    public static String token(String username, String userId, String... roles) {
        return token(username, userId, 3_600_000L, roles);
    }

    public static String expired(String username, String userId, String... roles) {
        return token(username, userId, -60_000L, roles);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String username, String userId, long ttlMs, String... roles) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("uid", userId)
                .claim("roles", List.of(roles))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();
    }
}
