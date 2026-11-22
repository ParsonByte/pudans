"use client";

import { useTheme } from "next-themes";

import { useIsClient } from "@/hooks/utils/use-is-client";

export function useThemeSync() {
    const { theme, resolvedTheme, systemTheme, setTheme } = useTheme();
    const isClient = useIsClient();
    const currentTheme = isClient ? resolvedTheme : undefined;
    const isDark = currentTheme === "dark";

    return {
        theme,
        resolvedTheme: currentTheme,
        systemTheme,
        isDark,
        isReady: isClient && Boolean(currentTheme),
        setTheme,
    };
}
