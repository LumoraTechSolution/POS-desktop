package com.lumora.pos.licensing.service;

import com.lumora.pos.licensing.config.LicenseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies signed license tokens on the desktop install using the Ed25519 public
 * key BAKED INTO THIS JAR (app.license.signing.public-key in application-desktop.yml).
 * The key is deliberately not accepted from the Electron layer — that independence
 * is what stops a patched launcher from minting its own licenses.
 */
@Slf4j
@Component
@Profile("desktop")
public class LicenseVerifier {

    /** Sentinel value left in application-desktop.yml until the build bakes the real key. */
    static final String PUBLIC_KEY_PLACEHOLDER = "REPLACE_WITH_ED25519_PUBLIC_KEY_BASE64";

    private final PublicKey publicKey;

    public LicenseVerifier(LicenseProperties properties) {
        String pub = properties.getSigning().getPublicKey();
        if (pub == null || pub.isBlank() || PUBLIC_KEY_PLACEHOLDER.equals(pub.trim())) {
            // Fail closed: a desktop build without a real verification key must not start.
            throw new IllegalStateException(
                    "Desktop build is missing its license public key. Bake the Ed25519 public key "
                            + "into application-desktop.yml (app.license.signing.public-key) before building.");
        }
        try {
            this.publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub.trim())));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid license public key in application-desktop.yml", e);
        }
    }

    /**
     * Verifies the token's signature and expiry. Throws {@code ExpiredJwtException}
     * if expired, or another {@code JwtException} if the signature/format is bad.
     */
    public Jws<Claims> verify(String token) {
        return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
    }
}
