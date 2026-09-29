package com.military.ams.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("role", principal.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parse(String token) {
        requireCanonicalSignature(token);
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Rejects any token whose signature segment is not a canonical base64url
     * encoding of its own bytes.
     *
     * <p>A 32 byte HMAC-SHA-256 signature occupies 43 base64url characters, which
     * carry 258 bits for 256 bits of data, so the final character's last two bits
     * are unused. Lenient decoders ignore those bits, which makes the signature
     * malleable: {@code ...w}, {@code ...x}, {@code ...y} and {@code ...z} all decode
     * to identical bytes. That lets one valid token be rewritten into three other
     * accepted strings, defeating anything that keys off the raw token text such as
     * denylists, revocation lists or audit correlation. Decoding and re-encoding the
     * segment and demanding an exact match is the standard way to reject it.</p>
     */
    private void requireCanonicalSignature(String token) {
        int lastDot = token.lastIndexOf('.');
        if (lastDot < 0 || lastDot == token.length() - 1) {
            throw new MalformedJwtException("Token is missing a signature segment");
        }
        String signature = token.substring(lastDot + 1);
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException ex) {
            throw new MalformedJwtException("Signature is not valid base64url", ex);
        }
        if (!signature.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))) {
            throw new MalformedJwtException("Signature is not a canonical base64url encoding");
        }
    }
}
