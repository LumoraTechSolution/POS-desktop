import { createHash } from "crypto";
import { execFileSync } from "child_process";
import os from "os";

/**
 * Hardware fingerprinting for license node-locking.
 *
 * The dominant factor is the Windows MachineGuid (HKLM\SOFTWARE\Microsoft\
 * Cryptography\MachineGuid) — it is per-OS-install and survives peripheral and
 * most hardware changes, so a legitimate customer is not locked out by swapping
 * a dock, NIC, or disk. Only an OS reinstall changes it, which is exactly when a
 * support-mediated re-activation is appropriate. When the GUID can't be read we
 * fall back to a composite of stable-ish identifiers.
 */

export interface FingerprintParts {
  machineGuid: string;
  hostname: string;
  mac: string;
  cpu: string;
}

function readMachineGuid(): string {
  try {
    const out = execFileSync(
      "reg",
      ["query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"],
      { encoding: "utf8", windowsHide: true },
    );
    const match = out.match(/MachineGuid\s+REG_SZ\s+([\w-]+)/i);
    return match ? match[1].trim() : "";
  } catch {
    return "";
  }
}

function primaryMac(): string {
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const ni of ifaces[name] ?? []) {
      if (!ni.internal && ni.mac && ni.mac !== "00:00:00:00:00:00") {
        return ni.mac;
      }
    }
  }
  return "";
}

export function fingerprintParts(): FingerprintParts {
  return {
    machineGuid: readMachineGuid(),
    hostname: os.hostname(),
    mac: primaryMac(),
    cpu: (os.cpus()[0]?.model ?? "").trim(),
  };
}

export function sha256Hex(input: string): string {
  return createHash("sha256").update(input, "utf8").digest("hex");
}

/**
 * Deterministic per-machine fingerprint. Must compute identically at activation
 * time and on every later launch, so it depends only on stable identifiers.
 */
export function computeFingerprint(): string {
  const parts = fingerprintParts();
  const basis = parts.machineGuid
    ? `guid:${parts.machineGuid}`
    : `composite:${parts.hostname}|${parts.mac}|${parts.cpu}`;
  return sha256Hex(basis);
}

/** Short, human-readable code shown on the activation screen for support calls. */
export function fingerprintShortCode(): string {
  const fp = computeFingerprint();
  return fp.slice(0, 16).toUpperCase().replace(/(.{4})(?=.)/g, "$1-");
}
