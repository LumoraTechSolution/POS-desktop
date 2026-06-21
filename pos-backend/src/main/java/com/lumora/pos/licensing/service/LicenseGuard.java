package com.lumora.pos.licensing.service;

import com.lumora.pos.licensing.config.LicenseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Second, independent license enforcement layer for the desktop build.
 *
 * <p>Verifies the signed license at startup with the JAR-baked public key (see
 * {@link LicenseVerifier}) and confirms it is bound to this machine. Because the
 * check runs in {@code @PostConstruct}, throwing here aborts the Spring context
 * before the web server starts — so even if the Electron launcher's JavaScript
 * checks are patched out, the backend simply refuses to serve and the POS is
 * inert.</p>
 */
@Slf4j
@Component
@Profile("desktop")
@RequiredArgsConstructor
public class LicenseGuard {

    private final LicenseProperties properties;
    private final LicenseVerifier verifier;
    private final MachineFingerprint machineFingerprint;

    @PostConstruct
    void enforce() {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            fail("no license token was provided to the backend");
        }

        Jws<Claims> jws;
        try {
            jws = verifier.verify(token);
        } catch (ExpiredJwtException e) {
            fail("the license has expired");
            return; // unreachable — fail() always throws
        } catch (Exception e) {
            fail("the license signature is invalid");
            return; // unreachable
        }

        Claims claims = jws.getPayload();
        String licensedFingerprint = claims.get("fp", String.class);
        if (licensedFingerprint == null || licensedFingerprint.isBlank()) {
            fail("the license is missing its machine binding");
        }

        String actualFingerprint = machineFingerprint.compute();
        if (actualFingerprint != null) {
            // Normal path: the backend independently recomputed the fingerprint.
            if (!actualFingerprint.equals(licensedFingerprint)) {
                fail("the license is bound to a different machine");
            }
        } else {
            // Couldn't read the hardware id here — fall back to the launcher's value.
            // The signature was still independently verified above.
            String launcherFingerprint = properties.getMachineFingerprint();
            if (launcherFingerprint == null || !launcherFingerprint.equals(licensedFingerprint)) {
                fail("unable to confirm this machine's identity");
            }
        }

        log.info("Desktop license verified for '{}' (edition {}).",
                claims.get("customer", String.class), claims.get("edition", String.class));
    }

    private void fail(String reason) {
        throw new IllegalStateException("Lumora POS cannot start — license check failed: " + reason);
    }
}
