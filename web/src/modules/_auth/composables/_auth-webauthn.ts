type AssertionOptions = {
    challenge: string;
    timeout?: number;
    rpId?: string;
    allowCredentials?: Array<{ type?: string; id: string }>;
    userVerification?: UserVerificationRequirement;
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
