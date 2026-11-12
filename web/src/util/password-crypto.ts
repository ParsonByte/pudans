/**
 * Minimal RSA/ECB/PKCS1Padding implementation for the sign-in form.
 *
 * The backend's CryptoService expects the password encrypted with the server's
 * RSA public key using PKCS#1 v1.5. WebCrypto only exposes RSA-OAEP, so the
 * PKCS#1 padding and modular exponentiation are implemented here with native
 * BigInt (the public exponent is normally 65537, so this stays cheap).
 */

interface DerReader {
  offset: number;
}

interface RsaPublicKey {
  modulus: bigint;
  exponent: bigint;
  modulusLength: number;
}

function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

function readLength(bytes: Uint8Array, reader: DerReader): number {
  const first = bytes[reader.offset];
  reader.offset += 1;
  if (first < 0x80) {
    return first;
  }
  const count = first & 0x7f;
  let length = 0;
  for (let index = 0; index < count; index += 1) {
    length = (length << 8) | bytes[reader.offset];
    reader.offset += 1;
  }
  return length;
}

function readTag(bytes: Uint8Array, reader: DerReader, expected: number): Uint8Array {
  const tag = bytes[reader.offset];
  if (tag !== expected) {
    throw new Error(
      `Unexpected DER tag 0x${tag.toString(16)}; expected 0x${expected.toString(16)}.`,
    );
  }
  reader.offset += 1;
  const length = readLength(bytes, reader);
  const value = bytes.subarray(reader.offset, reader.offset + length);
  reader.offset += length;
  return value;
}

function readInteger(bytes: Uint8Array, reader: DerReader): bigint {
  const value = readTag(bytes, reader, 0x02);
  let offset = 0;
  while (offset < value.length - 1 && value[offset] === 0) {
    offset += 1;
  }
  let result = 0n;
  for (let index = offset; index < value.length; index += 1) {
    result = (result << 8n) | BigInt(value[index]);
  }
  return result;
}

function parsePublicKey(base64: string): RsaPublicKey {
  const der = base64ToBytes(base64.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""));
  const reader: DerReader = { offset: 0 };
  const spki = readTag(der, reader, 0x30);
  const spkiReader: DerReader = { offset: 0 };
  // AlgorithmIdentifier (rsaEncryption + NULL)
  readTag(spki, spkiReader, 0x30);
  // BIT STRING wrapping the RSAPublicKey
  const bitString = readTag(spki, spkiReader, 0x03);
  if (bitString[0] !== 0) {
    throw new Error("Unsupported RSA public key bit string.");
  }
  const keyReader: DerReader = { offset: 0 };
  const rsaKey = readTag(bitString.subarray(1), keyReader, 0x30);
  const integerReader: DerReader = { offset: 0 };
  const modulus = readInteger(rsaKey, integerReader);
  const exponent = readInteger(rsaKey, integerReader);
  const modulusLength = Math.ceil(modulus.toString(2).length / 8);
  return { modulus, exponent, modulusLength };
}

function modPow(base: bigint, exponent: bigint, modulus: bigint): bigint {
  let result = 1n;
  let factor = base % modulus;
  let power = exponent;
  while (power > 0n) {
    if (power & 1n) {
      result = (result * factor) % modulus;
    }
    power >>= 1n;
    factor = (factor * factor) % modulus;
  }
  return result;
}

function bytesToBigInt(bytes: Uint8Array): bigint {
  let result = 0n;
  for (const byte of bytes) {
    result = (result << 8n) | BigInt(byte);
  }
  return result;
}

function bigIntToBytes(value: bigint, length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  let remaining = value;
  for (let index = length - 1; index >= 0; index -= 1) {
    bytes[index] = Number(remaining & 0xffn);
    remaining >>= 8n;
  }
  return bytes;
}

function randomNonZeroBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  let offset = 0;
  while (offset < length) {
    const chunk = new Uint8Array(length - offset);
    crypto.getRandomValues(chunk);
    for (const byte of chunk) {
      if (byte !== 0) {
        bytes[offset] = byte;
        offset += 1;
      }
    }
  }
  return bytes;
}

/**
 * Encrypts the password with the server's RSA public key using PKCS#1 v1.5,
 * matching the backend's RSA/ECB/PKCS1Padding CryptoService.
 */
export function encryptPassword(password: string, publicKeyBase64: string): string {
  const key = parsePublicKey(publicKeyBase64);
  const message = new TextEncoder().encode(password);
  if (message.length > key.modulusLength - 11) {
    throw new Error("The password is too long for the server RSA key.");
  }

  const paddingLength = key.modulusLength - message.length - 3;
  const em = new Uint8Array(key.modulusLength);
  em[0] = 0x00;
  em[1] = 0x02;
  em.set(randomNonZeroBytes(paddingLength), 2);
  em[2 + paddingLength] = 0x00;
  em.set(message, paddingLength + 3);

  const encrypted = modPow(bytesToBigInt(em), key.exponent, key.modulus);
  return bytesToBase64(bigIntToBytes(encrypted, key.modulusLength));
}
