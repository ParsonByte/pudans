"use client";

import { useEffect, useState } from "react";

import { useThemeSync } from "@/hooks/ui/use-theme-sync";

export type DesignTokens = {
    borderRadiusLG: string;
    boxShadowSecondary: string;
    boxShadowTertiary: string;
    colorBgContainer: string;
    colorBgElevated: string;
    colorBorder: string;
    colorBorderSecondary: string;
    colorError: string;
    colorErrorBg: string;
    colorErrorBorder: string;
    colorFillQuaternary: string;
    colorFillSecondary: string;
    colorFillTertiary: string;
    colorInfo: string;
    colorInfoBg: string;
    colorInfoBorder: string;
    colorLink: string;
    colorPrimary: string;
    colorPrimaryBg: string;
    colorPrimaryBorder: string;
    colorSplit: string;
    colorSuccess: string;
    colorSuccessBg: string;
    colorSuccessBorder: string;
    colorText: string;
    colorTextLightSolid: string;
    colorTextQuaternary: string;
    colorTextSecondary: string;
    colorTextTertiary: string;
    colorWarning: string;
    colorWarningBg: string;
    colorWarningBorder: string;
};

const CSS_DESIGN_TOKENS: DesignTokens = {
    borderRadiusLG: "var(--td-radius-extraLarge)",
    boxShadowSecondary: "var(--td-shadow-2)",
    boxShadowTertiary: "var(--td-shadow-1)",
    colorBgContainer: "var(--td-bg-color-container)",
    colorBgElevated: "var(--td-bg-color-container)",
    colorBorder: "var(--td-border-level-2-color)",
    colorBorderSecondary: "var(--td-border-level-1-color)",
    colorError: "var(--td-error-color)",
    colorErrorBg: "var(--td-error-color-light)",
    colorErrorBorder: "var(--td-error-color-3)",
    colorFillQuaternary: "var(--td-bg-color-secondarycontainer)",
    colorFillSecondary: "var(--td-bg-color-component)",
    colorFillTertiary: "var(--td-bg-color-container-hover)",
    colorInfo: "var(--td-brand-color)",
    colorInfoBg: "var(--td-brand-color-light)",
    colorInfoBorder: "var(--td-brand-color-3)",
    colorLink: "var(--td-text-color-link)",
    colorPrimary: "var(--td-brand-color)",
    colorPrimaryBg: "var(--td-brand-color-light)",
    colorPrimaryBorder: "var(--td-brand-color-3)",
    colorSplit: "var(--td-component-stroke)",
    colorSuccess: "var(--td-success-color)",
    colorSuccessBg: "var(--td-success-color-light)",
    colorSuccessBorder: "var(--td-success-color-3)",
    colorText: "var(--td-text-color-primary)",
    colorTextLightSolid: "var(--td-text-color-anti)",
    colorTextQuaternary: "var(--td-text-color-disabled)",
    colorTextSecondary: "var(--td-text-color-secondary)",
    colorTextTertiary: "var(--td-text-color-placeholder)",
    colorWarning: "var(--td-warning-color)",
    colorWarningBg: "var(--td-warning-color-light)",
    colorWarningBorder: "var(--td-warning-color-3)",
};

const CSS_VARIABLE_PATTERN = /^var\((--[^)]+)\)$/;

function resolveCssValue(style: CSSStyleDeclaration, value: string, depth = 0): string {
    const match = CSS_VARIABLE_PATTERN.exec(value.trim());

    if (!match || depth > 4) {
        return value;
    }

    const resolved = style.getPropertyValue(match[1]).trim();

    return resolved ? resolveCssValue(style, resolved, depth + 1) : value;
}

function readDesignTokens(): DesignTokens {
    if (typeof window === "undefined") {
        return CSS_DESIGN_TOKENS;
    }

    const style = window.getComputedStyle(document.documentElement);

    return Object.fromEntries(
        Object.entries(CSS_DESIGN_TOKENS).map(([key, value]) => [key, resolveCssValue(style, value)]),
    ) as DesignTokens;
}

export function useDesignTokens(): DesignTokens {
    const { isReady, resolvedTheme } = useThemeSync();
    const [tokens, setTokens] = useState<DesignTokens>(CSS_DESIGN_TOKENS);

    useEffect(() => {
        if (!isReady || !resolvedTheme) {
            return;
        }

        const frame = window.requestAnimationFrame(() => {
            setTokens(readDesignTokens());
        });

        return () => window.cancelAnimationFrame(frame);
    }, [isReady, resolvedTheme]);

    return tokens;
}
