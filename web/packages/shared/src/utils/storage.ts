export interface StorageAdapter {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
}

export interface JsonStorage<T> {
  read: () => T;
  write: (value: T) => T;
  update: (patch: Partial<T>) => T;
  remove: () => void;
}

export function getBrowserStorage(): StorageAdapter | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }

  try {
    const probe = `__pudans_probe__${Date.now()}`;
    window.localStorage.setItem(probe, "1");
    window.localStorage.removeItem(probe);
    return window.localStorage;
  } catch {
    return undefined;
  }
}

export function createJsonStorage<T>(
  key: string,
  fallback: T,
  storage: StorageAdapter | undefined = getBrowserStorage(),
): JsonStorage<T> {
  const read = (): T => {
    if (!storage) {
      return fallback;
    }

    try {
      const raw = storage.getItem(key);
      return raw === null ? fallback : (JSON.parse(raw) as T);
    } catch {
      return fallback;
    }
  };

  const write = (value: T): T => {
    try {
      storage?.setItem(key, JSON.stringify(value));
    } catch {
      // Storage can be unavailable in private mode or when the quota is full.
    }
    return value;
  };

  return {
    read,
    write,
    update: (patch) => {
      const current = read();
      const next: T =
        typeof current === "object" && current !== null && !Array.isArray(current)
          ? { ...current, ...patch }
          : (patch as T);
      return write(next);
    },
    remove: () => {
      try {
        storage?.removeItem(key);
      } catch {
        // Ignore storage failures; callers should not crash on cleanup.
      }
    },
  };
}
