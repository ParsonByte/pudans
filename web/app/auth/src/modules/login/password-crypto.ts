import { JSEncrypt } from "jsencrypt";

function toPem(publicKeyBase64: string): string {
  const normalized = publicKeyBase64.replace(/\s+/g, "");
  const lines = normalized.match(/.{1,64}/g) ?? [normalized];
  return `-----BEGIN PUBLIC KEY-----\n${lines.join("\n")}\n-----END PUBLIC KEY-----`;
}

/**
 * Encrypts the password with the server's RSA public key using PKCS#1 v1.5,
 * matching the backend's RSA/ECB/PKCS1Padding CryptoService.
 */
export function encryptPassword(
  password: string,
  publicKeyBase64: string,
): string {
  const encryptor = new JSEncrypt();
  encryptor.setPublicKey(toPem(publicKeyBase64));
  const encrypted = encryptor.encrypt(password);
  if (!encrypted) {
    throw new Error("Could not encrypt the password with the server public key.");
  }
  return encrypted;
}
