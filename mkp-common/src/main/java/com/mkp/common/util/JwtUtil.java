package com.mkp.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET = System.getenv("JWT_SECRET") != null
            ? System.getenv("JWT_SECRET")
            : "default-dev-secret-key-do-not-use-in-prod";
    private static final long EXPIRE = 7 * 24 * 60 * 60 * 1000L;
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(KEY)
                .compact();
    }

    private static Claims getClaims(String token) {
        return Jwts.parser().verifyWith(KEY).build()
                .parseSignedClaims(token).getPayload();
    }

    public static Long getUserId(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    public static String getUsername(String token) {
        return getClaims(token).get("username", String.class);
    }

    public static String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public static boolean isExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean validateToken(String token) {
        try {
            getClaims(token);
            return !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
