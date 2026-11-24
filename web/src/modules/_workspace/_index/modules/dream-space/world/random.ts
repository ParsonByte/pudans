export type SeededRandom = () => number;

export function createSeededRandom(seed: number): SeededRandom {
    let state = seed >>> 0;

    return () => {
        state += 0x6d2b79f5;
        let value = state;
        value = Math.imul(value ^ (value >>> 15), value | 1);
        value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
        return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
    };
}

export function hashString(value: string): number {
    let hash = 2166136261;

    for (let index = 0; index < value.length; index += 1) {
        hash ^= value.charCodeAt(index);
        hash = Math.imul(hash, 16777619);
    }

    return hash >>> 0;
}

export function numberInRange(random: SeededRandom, min: number, max: number): number {
    return min + random() * (max - min);
}

export function integerInRange(random: SeededRandom, min: number, max: number): number {
    return Math.floor(numberInRange(random, min, max + 1));
}

export function pickOne<T>(random: SeededRandom, values: readonly T[]): T {
    return values[Math.floor(random() * values.length)] ?? values[0];
}
