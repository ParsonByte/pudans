"use client";

import { useCallback, useMemo } from "react";

import {
    blue,
    blueDark,
    cyan,
    cyanDark,
    geekblue,
    geekblueDark,
    gold,
    goldDark,
    green,
    greenDark,
    lime,
    limeDark,
    magenta,
    magentaDark,
    orange,
    orangeDark,
    purple,
    purpleDark,
    red,
    redDark,
    volcano,
    volcanoDark,
} from "@ant-design/colors";
import { theme } from "antd";
import { useTheme } from "next-themes";

const TONE_PALETTES = {
    blue: [blue, blueDark],
    cyan: [cyan, cyanDark],
    geekblue: [geekblue, geekblueDark],
    gold: [gold, goldDark],
    green: [green, greenDark],
    lime: [lime, limeDark],
    magenta: [magenta, magentaDark],
    orange: [orange, orangeDark],
    purple: [purple, purpleDark],
    red: [red, redDark],
    volcano: [volcano, volcanoDark],
} as const;

export type DashboardTone = keyof typeof TONE_PALETTES;

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

function toneColors(name: DashboardTone, dark: boolean): DashboardToneColors {
    const [lightPalette, darkPalette] = TONE_PALETTES[name];
    const palette = dark ? darkPalette : lightPalette;

    return {
        base: palette[dark ? 7 : 5],
        border: palette[2],
        soft: palette[dark ? 1 : 0],
    };
}

export function useDashboardTheme() {
    const { token } = theme.useToken();
    const { resolvedTheme } = useTheme();
    const dark = resolvedTheme === "dark";
    const tone = useCallback((name: DashboardTone) => toneColors(name, dark), [dark]);
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
