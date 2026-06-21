package com.lumora.pos.hardware.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class QzSigningServiceTest {

    @Test
    void isUnconfiguredWhenNoKeyProvided() {
        QzSigningService svc = new QzSigningService("", "");
        assertThat(svc.isConfigured()).isFalse();
    }

    @Test
    void isUnconfiguredWhenKeyIsGarbage() {
        QzSigningService svc = new QzSigningService("cert", "not-a-real-key");
        assertThat(svc.isConfigured()).isFalse();
    }

    @Test
    void signsPayloadVerifiableWithPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        QzSigningService svc = new QzSigningService("dummy-cert", pem);
        assertThat(svc.isConfigured()).isTrue();
        assertThat(svc.getCertificate()).isEqualTo("dummy-cert");

        String data = "qz-request-payload";
        byte[] signature = Base64.getDecoder().decode(svc.sign(data));

        Signature verifier = Signature.getInstance("SHA512withRSA");
        verifier.initVerify(kp.getPublic());
        verifier.update(data.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(signature)).isTrue();
    }
}
