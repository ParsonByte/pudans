import type { WebAuthnAuthenticationOptions } from "@pudans-dev/api-client";

export type PasskeyAssertion = {
  authenticatorData: string;
  clientDataJson: string;
  credentialId: string;
  signature: string;
};

export function passkeySupported() {
  return typeof window !== "undefined"
    && typeof window.PublicKeyCredential !== "undefined"
    && typeof navigator.credentials !== "undefined";
}

export function base64UrlToBuffer(value: string): ArrayBuffer {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
}

export function bufferToBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/u, "");
}

export async function requestPasskeyAssertion(
  options: WebAuthnAuthenticationOptions
): Promise<PasskeyAssertion> {
  if (!options.challenge || !options.challengeId) {
    throw new Error("通行密钥选项不完整");
  }
  if (!passkeySupported()) {
    throw new Error("当前浏览器不支持通行密钥");
  }
  const credential = await navigator.credentials.get({
    publicKey: {
      allowCredentials: (options.allowCredentials ?? []).map((descriptor) => ({
        id: base64UrlToBuffer(descriptor.id ?? ""),
        transports: descriptor.transports as AuthenticatorTransport[] | undefined,
        type: "public-key" as const,
      })),
      challenge: base64UrlToBuffer(options.challenge),
      rpId: options.rpId,
      timeout: options.timeout,
      userVerification: (options.userVerification as UserVerificationRequirement | undefined)
        ?? "preferred",
    },
  });
  if (!credential || !(credential instanceof PublicKeyCredential)) {
    throw new Error("未获得通行密钥响应");
  }
  const response = credential.response;
  if (!(response instanceof AuthenticatorAssertionResponse)) {
    throw new Error("通行密钥响应类型不正确");
  }
  return {
    authenticatorData: bufferToBase64Url(response.authenticatorData),
    clientDataJson: bufferToBase64Url(response.clientDataJSON),
    credentialId: bufferToBase64Url(credential.rawId),
    signature: bufferToBase64Url(response.signature),
  };
}