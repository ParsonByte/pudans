/**
 * Bit-set helper backed by a base64 encoded byte string. Migrated from the
 * legacy frontend util/boolset so capability/bitfield responses can be decoded
 * without a bespoke parser per API.
 */
export class Boolset {
  private data: Uint8Array;

  constructor(data?: string, raw?: Uint8Array) {
    if (!data) {
      this.data = raw ? raw : new Uint8Array(0);
      return;
    }
    try {
      const binary = atob(data);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      this.data = bytes;
    } catch {
      this.data = new Uint8Array(0);
    }
  }

  enabled(index: number): boolean {
    const byteIndex = Math.floor(index / 8);
    if (index < 0 || byteIndex >= this.data.length) {
      return false;
    }
    return (this.data[byteIndex] & (1 << index % 8)) !== 0;
  }

  and(other: Boolset): Boolset {
    const length = Math.max(this.data.length, other.data.length);
    const result = new Uint8Array(length);
    for (let index = 0; index < length; index += 1) {
      result[index] = (this.data[index] ?? 0) & (other.data[index] ?? 0);
    }
    return new Boolset(undefined, result);
  }

  or(other: Boolset): Boolset {
    const length = Math.max(this.data.length, other.data.length);
    const result = new Uint8Array(length);
    for (let index = 0; index < length; index += 1) {
      result[index] = (this.data[index] ?? 0) | (other.data[index] ?? 0);
    }
    return new Boolset(undefined, result);
  }

  set(index: number, enabled: boolean): Boolset {
    if (index < 0) {
      return this;
    }
    if (index >= this.data.length * 8) {
      const next = new Uint8Array(Math.ceil((index + 1) / 8));
      next.set(this.data);
      this.data = next;
    }
    const byteIndex = Math.floor(index / 8);
    if (enabled) {
      this.data[byteIndex] |= 1 << index % 8;
    } else {
      this.data[byteIndex] &= ~(1 << index % 8);
    }
    return this;
  }

  sets(values: Record<number, boolean>): Boolset {
    for (const key of Object.keys(values)) {
      this.set(Number.parseInt(key, 10), values[Number(key)] ?? false);
    }
    return this;
  }

  toUint8Array(): Uint8Array {
    return this.data.slice();
  }

  toString(): string {
    return btoa(String.fromCharCode(...Array.from(this.data)));
  }
}
