/** UTF-8 safe base64 helpers (migrated from the legacy frontend util/base64). */

export function base64Encode(data: string | Uint8Array): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return btoa(binary);
}

export function base64Decode(data: string): Uint8Array {
  return Uint8Array.from(atob(data), (character) => character.charCodeAt(0));
}

export function base64DecodeToString(data: string): string {
  return new TextDecoder().decode(base64Decode(data));
}
