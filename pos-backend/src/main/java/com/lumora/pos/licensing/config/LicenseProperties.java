package com.lumora.pos.licensing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds app.license.* properties. The signing key pair is Ed25519:
 * the private key lives only in the cloud, the public key is embedded in the
 * desktop app and the backend desktop guard so licenses verify offline.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.license")
public class LicenseProperties {

    /** Issuer string stamped into every signed license token. */
    private String issuer = "lumora-pos";

    /** Default license lifetime in days when issuing a key. 0 = perpetual. */
    private int defaultValidityDays = 0;

    /**
     * Desktop profile only: the signed license token, injected by the Electron
     * launcher (APP_LICENSE_TOKEN). The backend verifies it on startup.
     */
    private String token;

    /**
     * Desktop profile only: the machine fingerprint computed by the launcher
     * (APP_MACHINE_FINGERPRINT). Used only as a fallback when the backend can't
     * read the hardware id itself.
     */
    private String machineFingerprint;

    private final Signing signing = new Signing();

    @Getter
    @Setter
    public static class Signing {
        /**
         * Base64-encoded PKCS#8 Ed25519 private key. Cloud-only secret.
         * Leave blank in dev — the service generates an ephemeral key pair and
         * logs it (licenses signed with it stop verifying after a restart).
         */
        private String privateKey;

        /** Base64-encoded X.509 Ed25519 public key. Matches {@link #privateKey}. */
        private String publicKey;
    }
}
