package com.lumora.pos.auth.service;

import com.lumora.pos.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the keyed blind index stored in {@code users.pin_lookup}: a
 * deterministic HMAC-SHA256 of the PIN, keyed by the server's JWT secret. Equal
 * PINs yield equal lookups (so collisions are a simple equality/group-by), but a
 * 4-digit space can't be reversed from the column without the secret. This is a
 * lookup aid only — bcrypt remains the verifier.
 */
@Component
@RequiredArgsConstructor
public class PinLookupHasher {

    private final JwtProperties jwtProperties;

    public String hash(String rawPin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawPin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute PIN lookup", e);
        }
    }
}
