/**
 * Small LRU cache migrated from the legacy frontend util/lru. Object URLs are
 * revoked on eviction by default so preview surfaces do not leak memory.
 */
export interface LRUCacheOptions<V> {
  /** Defaults to revoking "blob:" URLs. */
  dispose?: (value: V) => void;
}

export class LRUCache<K, V> {
  private readonly capacity: number;
  private readonly cache = new Map<K, V>();
  private readonly dispose: (value: V) => void;

  constructor(capacity: number, options: LRUCacheOptions<V> = {}) {
    this.capacity = Math.max(1, capacity);
    this.dispose =
      options.dispose ??
      ((value) => {
        if (typeof value === "string" && value.startsWith("blob:")) {
          URL.revokeObjectURL(value);
        }
      });
  }

  get(key: K): V | undefined {
    if (!this.cache.has(key)) {
      return undefined;
    }
    const value = this.cache.get(key) as V;
    this.cache.delete(key);
    this.cache.set(key, value);
    return value;
  }

  set(key: K, value: V): void {
    if (this.cache.has(key)) {
      this.cache.delete(key);
    } else if (this.cache.size >= this.capacity) {
      const oldestKey = this.cache.keys().next().value as K | undefined;
      if (oldestKey !== undefined) {
        const oldestValue = this.cache.get(oldestKey);
        this.cache.delete(oldestKey);
        if (oldestValue !== undefined) {
          this.dispose(oldestValue);
        }
      }
    }
    this.cache.set(key, value);
  }

  has(key: K): boolean {
    return this.cache.has(key);
  }

  delete(key: K): boolean {
    return this.cache.delete(key);
  }

  clear(): void {
    for (const value of this.cache.values()) {
      this.dispose(value);
    }
    this.cache.clear();
  }

  get size(): number {
    return this.cache.size;
  }
}
