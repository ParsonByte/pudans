type AssertionOptions = {
    challenge: string;
    timeout?: number;
    rpId?: string;
    allowCredentials?: Array<{ type?: string; id: string }>;
    userVerification?: UserVerificationRequirement;
};

type RegistrationOptions = {
    attestation?: AttestationConveyancePreference;
    authenticatorSelection?: AuthenticatorSelectionCriteria;
    challenge: string;
    excludeCredentials?: Array<{ type?: string; id: string }>;
    pubKeyCredParams?: Array<{ alg: number; type?: string }>;
    rp?: { id?: string; name?: string };
    timeout?: number;
    user: { displayName?: string; id: string; name: string };
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

export async function getPasskeyAssertion(options: Record<string, unknown>): Promise<PublicKeyCredential> {
    const publicKey = options as AssertionOptions;

    if (!publicKey.challenge) {
        throw new Error("通行密钥挑战缺失。");
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
        throw new Error("浏览器未返回通行密钥。");
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

export async function createPasskeyRegistration(options: Record<string, unknown>): Promise<string> {
    if (typeof window === "undefined" || !window.PublicKeyCredential || !navigator.credentials) {
        throw new Error("当前浏览器不支持通行密钥。");
    }

    const publicKey = options as unknown as RegistrationOptions;

    if (!publicKey.challenge || !publicKey.user?.id) {
        throw new Error("通行密钥注册参数缺失。");
    }

    const credential = await navigator.credentials.create({
        publicKey: {
            attestation: publicKey.attestation,
            authenticatorSelection: publicKey.authenticatorSelection,
            challenge: base64UrlToBuffer(publicKey.challenge),
            excludeCredentials: publicKey.excludeCredentials?.map((descriptor) => ({
                type: "public-key" as const,
                id: base64UrlToBuffer(descriptor.id),
            })),
            pubKeyCredParams:
                publicKey.pubKeyCredParams?.map((parameter) => ({
                    alg: parameter.alg,
                    type: "public-key" as const,
                })) ?? [],
            rp: {
                id: publicKey.rp?.id,
                name: publicKey.rp?.name ?? window.location.hostname,
            },
            timeout: publicKey.timeout,
            user: {
                displayName: publicKey.user.displayName ?? publicKey.user.name,
                id: base64UrlToBuffer(publicKey.user.id),
                name: publicKey.user.name,
            },
        },
    });

    if (!(credential instanceof PublicKeyCredential)) {
        throw new Error("浏览器未返回通行密钥。");
    }

    const response = credential.response as AuthenticatorAttestationResponse;

    return JSON.stringify({
        authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
        clientExtensionResults: credential.getClientExtensionResults(),
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        response: {
            attestationObject: bufferToBase64Url(response.attestationObject),
            clientDataJSON: bufferToBase64Url(response.clientDataJSON),
            transports: response.getTransports?.(),
        },
        type: credential.type,
    });
}
