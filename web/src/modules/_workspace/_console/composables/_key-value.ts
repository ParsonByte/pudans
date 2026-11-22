export type KeyValueEntry = {
    key?: string;
    value?: string;
};

export function toKeyValueEntries(data?: Record<string, string>): KeyValueEntry[] {
    return Object.entries(data ?? {}).map(([key, value]) => ({ key, value }));
}

export function toKeyValueRecord(entries?: KeyValueEntry[]): Record<string, string> {
    return Object.fromEntries(
        (entries ?? []).filter((entry) => entry.key).map((entry) => [entry.key as string, entry.value ?? ""]),
    );
}
