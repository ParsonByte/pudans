"use client";

import { useCallback, useMemo } from "react";

import { useTheme } from "next-themes";
import { Color } from "tvision-color";

import { useDesignTokens } from "@/components/ui";

const TONE_BASES = {
    blue: "#0052d9",
    cyan: "#0594fa",
    geekblue: "#3b5bdb",
    gold: "#e6a800",
    green: "#2ba471",
    lime: "#7ea61a",
    magenta: "#d549b4",
    orange: "#f6703d",
    purple: "#834ec2",
    red: "#d54941",
    volcano: "#e34d59",
    warning: "#e37318",
} as const;

const TONE_BASES_DARK = {
    blue: "#4582e6",
    cyan: "#3ac7f5",
    geekblue: "#7c95f5",
    gold: "#f0b429",
    green: "#059465",
    lime: "#a5d24b",
    magenta: "#f17ac8",
    orange: "#f59a5b",
    purple: "#a986e8",
    red: "#c64751",
    volcano: "#f07070",
    warning: "#cf6e2d",
} as const;

export type DashboardTone = keyof typeof TONE_BASES;

export type DashboardToneColors = {
    base: string;
    border: string;
    soft: string;
};

const CHART_TONES: DashboardTone[] = [
    "blue",
    "gold",
    "red",
    "cyan",
    "green",
    "lime",
    "purple",
    "magenta",
    "volcano",
    "geekblue",
    "orange",
    "blue",
];

type TonePalette = {
    colors: string[];
    primary: number;
};

function buildPalettes(bases: Record<DashboardTone, string>): Record<DashboardTone, TonePalette> {
    const names = Object.keys(bases) as DashboardTone[];
    const gradations = Color.getColorGradations({
        colors: names.map((name) => bases[name]),
        remainInput: true,
        step: 10,
    });

    return Object.fromEntries(
        names.map((name, index) => [name, gradations[index] ?? { colors: [bases[name]], primary: 0 }]),
    ) as Record<DashboardTone, TonePalette>;
}

function resolveTokenColor(value: string, fallback: string): string {
    return value.startsWith("var(") ? fallback : value;
}

function toneColors(
    name: DashboardTone,
    dark: boolean,
    palettes: Record<DashboardTone, TonePalette>,
): DashboardToneColors {
    const palette = palettes[name];
    const colors = palette.colors;
    const primary = Math.max(0, Math.min(palette.primary, colors.length - 1));
    const border = colors[Math.min(2, colors.length - 1)] ?? colors[primary];
    const soft = colors[dark ? Math.min(1, colors.length - 1) : 0] ?? border;

    return {
        base: colors[primary],
        border,
        soft,
    };
}

export function useDashboardTheme() {
    const token = useDesignTokens();
    const { resolvedTheme } = useTheme();
    const dark = resolvedTheme === "dark";
    const palettes = useMemo(() => {
        const bases = { ...(dark ? TONE_BASES_DARK : TONE_BASES) };

        return buildPalettes({
            ...bases,
            blue: resolveTokenColor(token.colorPrimary, bases.blue),
            green: resolveTokenColor(token.colorSuccess, bases.green),
            red: resolveTokenColor(token.colorError, bases.red),
            warning: resolveTokenColor(token.colorWarning, bases.warning),
        });
    }, [dark, token]);
    const tone = useCallback((name: DashboardTone) => toneColors(name, dark, palettes), [dark, palettes]);
    const chartColors = useMemo(() => CHART_TONES.map((name) => tone(name).base), [tone]);

    return useMemo(
        () => ({
            chartColors,
            dark,
            token,
            tone,
        }),
        [chartColors, dark, token, tone],
    );
}
