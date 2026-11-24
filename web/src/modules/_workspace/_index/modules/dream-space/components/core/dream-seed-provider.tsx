"use client";

import { type ReactNode, createContext, useContext } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

type DreamSeedValue = {
    seed: number;
    shortCode: string;
    version: number;
};

const DreamSeedContext = createContext<DreamSeedValue | null>(null);

export function DreamSeedProvider({ children }: { children: ReactNode }) {
    const world = useDreamStore((state) => state.world);

    if (!world) {
        return <>{children}</>;
    }

    return (
        <DreamSeedContext.Provider value={{ seed: world.seed, shortCode: world.shortCode, version: world.version }}>
            {children}
        </DreamSeedContext.Provider>
    );
}

export function useDreamSeed(): DreamSeedValue {
    const value = useContext(DreamSeedContext);

    if (!value) {
        return { seed: 0, shortCode: "PENDING", version: 1 };
    }

    return value;
}
