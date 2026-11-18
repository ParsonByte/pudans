type AssertionOptions = {
  challenge: string;
  timeout?: number;
  rpId?: string;
  allowCredentials?: Array<{ type?: string; id: string }>;
  userVerification?: UserVerificationRequirement;
};

type RegistrationOptions = {
  challenge: string;
  rp: { id?: string; name: string };
  user: { id: string; name: string; displayName: string };
  pubKeyCredParams?: Array<{ type?: string; alg: number }>;
  timeout?: number;
  attestation?: AttestationConveyancePreference;
  authenticatorSelection?: AuthenticatorSelectionCriteria;
  excludeCredentials?: Array<{ type?: string; id: string }>;
};

function base64UrlToBuffer(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes.buffer;
}

function bufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";

  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function getPasskeyAssertion(
  options: Record<string, unknown>,
): Promise<PublicKeyCredential> {
  const publicKey = options as AssertionOptions;

  if (!publicKey.challenge) {
    throw new Error("Missing passkey challenge.");
  }

  const credential = await navigator.credentials.get({
    publicKey: {
      challenge: base64UrlToBuffer(publicKey.challenge),
      timeout: publicKey.timeout,
      rpId: publicKey.rpId,
      allowCredentials: publicKey.allowCredentials?.map((descriptor) => ({
        type: "public-key" as const,
        id: base64UrlToBuffer(descriptor.id),
      })),
      userVerification: publicKey.userVerification ?? "preferred",
    },
  });

  if (!(credential instanceof PublicKeyCredential)) {
    throw new Error("No passkey credential returned.");
  }

  return credential;
}

export function serializeAssertion(credential: PublicKeyCredential): string {
  const response = credential.response as AuthenticatorAssertionResponse;
  const responseJson: Record<string, string> = {
    clientDataJSON: bufferToBase64Url(response.clientDataJSON),
    authenticatorData: bufferToBase64Url(response.authenticatorData),
    signature: bufferToBase64Url(response.signature),
  };

  if (response.userHandle) {
    responseJson.userHandle = bufferToBase64Url(response.userHandle);
  }

  return JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: responseJson,
    clientExtensionResults: credential.getClientExtensionResults(),
  });
}

export async function createPasskey(
  options: Record<string, unknown>,
): Promise<PublicKeyCredential> {
  const publicKey = options as RegistrationOptions;

  if (!publicKey.challenge) {
    throw new Error("Missing passkey challenge.");
  }

  const credential = await navigator.credentials.create({
    publicKey: {
      challenge: base64UrlToBuffer(publicKey.challenge),
      rp: publicKey.rp,
      user: {
        name: publicKey.user.name,
        displayName: publicKey.user.displayName,
        id: base64UrlToBuffer(publicKey.user.id),
      },
      pubKeyCredParams:
        publicKey.pubKeyCredParams?.map((param) => ({
          type: "public-key" as const,
          alg: param.alg,
        })) ?? [],
      timeout: publicKey.timeout,
      attestation: publicKey.attestation,
      authenticatorSelection: publicKey.authenticatorSelection,
      excludeCredentials: publicKey.excludeCredentials?.map((descriptor) => ({
        type: "public-key" as const,
        id: base64UrlToBuffer(descriptor.id),
      })),
    },
  });

  if (!(credential instanceof PublicKeyCredential)) {
    throw new Error("No passkey credential returned.");
  }

  return credential;
}

export function serializeRegistration(credential: PublicKeyCredential): string {
  const response = credential.response as AuthenticatorAttestationResponse;
  return JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      attestationObject: bufferToBase64Url(response.attestationObject),
      transports:
        typeof response.getTransports === "function" ? response.getTransports() : undefined,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
  });
}
