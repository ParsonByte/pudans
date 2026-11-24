"use client";

import { type ReactNode, createContext, useContext } from "react";

import type { RegionDefinition, RegionTheme } from "../../types";

type RegionThemeValue = {
    region: RegionDefinition;
    theme: RegionTheme;
};

const RegionThemeContext = createContext<RegionThemeValue | null>(null);

export function RegionThemeProvider({ children, region }: { children: ReactNode; region: RegionDefinition }) {
    return (
        <RegionThemeContext.Provider value={{ region, theme: region.theme }}>{children}</RegionThemeContext.Provider>
    );
}

export function useRegionTheme(): RegionThemeValue {
    const value = useContext(RegionThemeContext);

    if (!value) {
        throw new Error("RegionThemeProvider is missing");
    }

    return value;
}
