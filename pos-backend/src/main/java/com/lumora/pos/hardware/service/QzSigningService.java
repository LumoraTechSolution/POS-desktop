package com.lumora.pos.hardware.service;

import com.lumora.pos.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs QZ Tray print requests so the cashier's QZ Tray app trusts this site and
 * prints silently (no per-job confirmation prompt).
 *
 * The certificate (public) and private key are supplied as PEM via configuration
 * — typically env-var secrets, never committed. When unset, {@link #isConfigured()}
 * is false and the frontend falls back to unsigned printing (one-time prompt).
 */
@Slf4j
@Service
public class QzSigningService {

    private final String certificate;
    private final PrivateKey privateKey;

    public QzSigningService(
            @Value("${app.qz.certificate:}") String certificate,
            @Value("${app.qz.private-key:}") String privateKeyPem) {
        this.certificate = certificate == null ? "" : certificate.trim();
        this.privateKey = parseKey(privateKeyPem);
    }

    public boolean isConfigured() {
        return !certificate.isBlank() && privateKey != null;
    }

    public String getCertificate() {
        return certificate;
    }

    /** Signs the QZ request payload, returning a base64 signature (SHA512withRSA). */
    public String sign(String data) {
        if (privateKey == null) {
            throw new BusinessException("QZ signing is not configured");
        }
        try {
            Signature signer = Signature.getInstance("SHA512withRSA");
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            log.error("Failed to sign QZ request", e);
            throw new BusinessException("Failed to sign QZ request");
        }
    }

    private PrivateKey parseKey(String pem) {
        if (pem == null || pem.isBlank()) return null;
        try {
            String normalized = pem
                    .replace("\\n", "\n")            // tolerate escaped newlines from env vars
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            log.warn("Could not parse QZ private key — silent printing disabled: {}", e.getMessage());
            return null;
        }
    }
}
