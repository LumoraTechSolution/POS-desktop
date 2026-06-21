import { createPublicKey, verify as cryptoVerify } from "crypto";

/**
 * Offline Ed25519 (EdDSA) JWS verification with zero external dependencies.
 *
 * The cloud signs license tokens with jjwt's {@code Jwts.SIG.EdDSA}, producing a
 * standard compact JWS whose signature is the raw 64-byte Ed25519 signature.
 * Node's {@code crypto.verify(null, data, ed25519PublicKey, sig)} verifies that
 * exact encoding, so the two sides interoperate without a JWT library here.
 */

export interface LicenseClaims {
  iss: string;
  iat: number;
  /** Unix seconds; absent for a perpetual license. */
  exp?: number;
  /** License key id (UUID). */
  kid: string;
  /** Bound machine fingerprint (SHA-256 hex). */
  fp: string;
  customer: string;
  edition: string;
  /** Comma-separated feature codes. */
  features: string;
  machine?: string;
}

function base64UrlToBuffer(value: string): Buffer {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  return Buffer.from(padded, "base64");
}

/**
 * Verifies the token's signature against the embedded public key and returns its
 * claims. Throws if the token is malformed, uses an unexpected algorithm, or the
 * signature does not check out. Does NOT enforce expiry or fingerprint — callers
 * apply those policy checks (see verifyLocalLicense).
 *
 * @param publicKeyBase64 Base64-encoded X.509/SPKI DER Ed25519 public key.
 */
export function verifyLicenseToken(token: string, publicKeyBase64: string): LicenseClaims {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Malformed license token");
  }
  const [headerB64, payloadB64, signatureB64] = parts;

  const header = JSON.parse(base64UrlToBuffer(headerB64).toString("utf8")) as { alg?: string };
  if (header.alg !== "EdDSA") {
    throw new Error(`Unexpected license algorithm: ${header.alg ?? "none"}`);
  }

  const publicKey = createPublicKey({
    key: Buffer.from(publicKeyBase64, "base64"),
    format: "der",
    type: "spki",
  });

  const signedData = Buffer.from(`${headerB64}.${payloadB64}`, "utf8");
  const signature = base64UrlToBuffer(signatureB64);

  // For Ed25519 the digest algorithm must be null.
  const ok = cryptoVerify(null, signedData, publicKey, signature);
  if (!ok) {
    throw new Error("Invalid license signature");
  }

  return JSON.parse(base64UrlToBuffer(payloadB64).toString("utf8")) as LicenseClaims;
}
