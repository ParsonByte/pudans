"use client";

import { useCallback, useMemo, useSyncExternalStore } from "react";

const LOCAL_STORAGE_EVENT = "local-storage-change";

function subscribe(key: string, onChange: () => void) {
    const handleStorage = (event: StorageEvent) => {
        if (event.key === null || event.key === key) {
            onChange();
        }
    };
    const handleLocal = () => onChange();

    window.addEventListener("storage", handleStorage);
    window.addEventListener(LOCAL_STORAGE_EVENT, handleLocal);

    return () => {
        window.removeEventListener("storage", handleStorage);
        window.removeEventListener(LOCAL_STORAGE_EVENT, handleLocal);
    };
}

function getServerSnapshot() {
    return null;
}

function readStoredValue<T>(key: string, fallback: T): T {
    try {
        const raw = window.localStorage.getItem(key);
        return raw === null ? fallback : (JSON.parse(raw) as T);
    } catch {
        return fallback;
    }
}

export function useLocalStorage<T>(key: string, initialValue: T) {
    const subscribeToKey = useCallback((onChange: () => void) => subscribe(key, onChange), [key]);

    const getSnapshot = useCallback(() => {
        try {
            return window.localStorage.getItem(key);
        } catch {
            return null;
        }
    }, [key]);

    const rawValue = useSyncExternalStore(subscribeToKey, getSnapshot, getServerSnapshot);

    const value = useMemo(() => {
        if (rawValue === null) {
            return initialValue;
        }
        try {
            return JSON.parse(rawValue) as T;
        } catch {
            return initialValue;
        }
    }, [rawValue, initialValue]);

    const setValue = useCallback(
        (nextValue: T | ((previousValue: T) => T)) => {
            try {
                const currentValue = readStoredValue(key, initialValue);
                const resolvedValue =
                    typeof nextValue === "function" ? (nextValue as (previousValue: T) => T)(currentValue) : nextValue;
                const serialized = JSON.stringify(resolvedValue);

                if (serialized === undefined) {
                    window.localStorage.removeItem(key);
                } else {
                    window.localStorage.setItem(key, serialized);
                }
                window.dispatchEvent(new Event(LOCAL_STORAGE_EVENT));
            } catch {
                // 本地存储不可用时保持当前状态，不影响页面交互。
            }
        },
        [key, initialValue],
    );

    return [value, setValue] as const;
}
