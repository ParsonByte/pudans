const BIGINT_ZERO = BigInt(0);
const BIGINT_ONE = BigInt(1);
const BIGINT_EIGHT = BigInt(8);
const BIGINT_BYTE_MASK = BigInt(0xff);

function decodeBase64(value: string): Uint8Array<ArrayBuffer> {
    const binary = atob(value);
    const bytes = new Uint8Array(new ArrayBuffer(binary.length));

    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
    }

    return bytes;
}

function decodeBase64Url(value: string): Uint8Array<ArrayBuffer> {
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/");

    return decodeBase64(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
}

function encodeBase64(value: Uint8Array): string {
    let binary = "";

    for (const byte of value) {
        binary += String.fromCharCode(byte);
    }

    return btoa(binary);
}

function bytesToBigInt(bytes: Uint8Array): bigint {
    let value = BIGINT_ZERO;

    for (const byte of bytes) {
        value = (value << BIGINT_EIGHT) | BigInt(byte);
    }

    return value;
}

function bigIntToBytes(value: bigint, length: number): Uint8Array {
    const bytes = new Uint8Array(length);
    let remaining = value;

    for (let index = length - 1; index >= 0; index -= 1) {
        bytes[index] = Number(remaining & BIGINT_BYTE_MASK);
        remaining >>= BIGINT_EIGHT;
    }

    return bytes;
}

function modPow(base: bigint, exponent: bigint, modulus: bigint): bigint {
    let result = BIGINT_ONE;
    let current = base % modulus;
    let power = exponent;

    while (power > BIGINT_ZERO) {
        if (power & BIGINT_ONE) {
            result = (result * current) % modulus;
        }
        current = (current * current) % modulus;
        power >>= BIGINT_ONE;
    }

    return result;
}

export async function encryptLoginPassword(password: string, base64Format: string): Promise<string> {
    if (!globalThis.crypto?.subtle) {
        throw new Error("当前浏览器不支持安全登录。");
    }

    const publicKey = await globalThis.crypto.subtle.importKey(
        "spki",
        decodeBase64(base64Format),
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        true,
        ["verify"],
    );
    const jwk = await globalThis.crypto.subtle.exportKey("jwk", publicKey);

    if (!jwk.n || !jwk.e) {
        throw new Error("登录公钥无效。");
    }

    const modulusBytes = decodeBase64Url(jwk.n);
    const modulus = bytesToBigInt(modulusBytes);
    const exponent = bytesToBigInt(decodeBase64Url(jwk.e));
    const message = new TextEncoder().encode(password);

    if (message.length > modulusBytes.length - 11) {
        throw new Error("密码过长。");
    }

    const paddingLength = modulusBytes.length - message.length - 3;
    const block = new Uint8Array(modulusBytes.length);
    const padding = new Uint8Array(paddingLength);

    while (padding.includes(0)) {
        globalThis.crypto.getRandomValues(padding);
    }

    block[0] = 0x00;
    block[1] = 0x02;
    block.set(padding, 2);
    block[2 + paddingLength] = 0x00;
    block.set(message, 3 + paddingLength);

    const encrypted = modPow(bytesToBigInt(block), exponent, modulus);

    return encodeBase64(bigIntToBytes(encrypted, modulusBytes.length));
}
