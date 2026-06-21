package com.lumora.pos.licensing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recomputes the machine fingerprint independently of the Electron launcher,
 * mirroring {@code electron/services/fingerprint.ts}: the SHA-256 hex of
 * {@code "guid:<MachineGuid>"}. Computing it here — rather than trusting a value
 * passed in by the desktop layer — is what makes the backend gate an independent
 * check that survives a patched launcher.
 *
 * <p>Returns {@code null} when the GUID can't be read, so the guard can fall back
 * to the launcher-supplied value (the signature check still applies regardless).</p>
 */
@Slf4j
@Component
@Profile("desktop")
public class MachineFingerprint {

    private static final Pattern GUID = Pattern.compile(
            "MachineGuid\\s+REG_SZ\\s+([\\w-]+)", Pattern.CASE_INSENSITIVE);

    /** SHA-256 hex of {@code "guid:<MachineGuid>"}, or null if the GUID is unavailable. */
    public String compute() {
        String guid = readMachineGuid();
        if (guid == null || guid.isBlank()) {
            return null;
        }
        return sha256Hex("guid:" + guid);
    }

    private String readMachineGuid() {
        try {
            Process p = new ProcessBuilder(
                    "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(5, TimeUnit.SECONDS);
            Matcher m = GUID.matcher(out);
            return m.find() ? m.group(1).trim() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Could not read MachineGuid; will fall back to the launcher fingerprint: {}",
                    e.getMessage());
            return null;
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
