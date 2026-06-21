/**
 * Ed25519 license-verification PUBLIC key, base64-encoded X.509/SPKI DER.
 *
 * This is a PUBLIC key — it is safe to ship inside the app. It can only *verify*
 * license tokens, never sign them; the private signing key never leaves the cloud.
 *
 * Obtain the real value before building the installer:
 *   GET /api/v1/super-admin/licenses/signing-public-key   (super-admin auth)
 * and paste the returned base64 string below. In dev, the backend logs an
 * ephemeral key pair on startup when app.license.signing.* is unset.
 *
 * Hardening (see docs/desktop-code-protection.md): this constant is compiled into
 * app.asar and protected by asar integrity + bytenode so it cannot be trivially
 * swapped for an attacker-controlled key.
 */
export const EMBEDDED_PUBLIC_KEY_PLACEHOLDER = "REPLACE_WITH_ED25519_PUBLIC_KEY_BASE64";

export const EMBEDDED_PUBLIC_KEY: string =
  "MCowBQYDK2VwAyEA29diU0Ca3bFJUeNSBA8WFi+mcv+m/mworWzP8m4sJVM=";

export function isPublicKeyConfigured(): boolean {
  return (
    !!EMBEDDED_PUBLIC_KEY &&
    EMBEDDED_PUBLIC_KEY !== EMBEDDED_PUBLIC_KEY_PLACEHOLDER &&
    EMBEDDED_PUBLIC_KEY.length > 20
  );
}
