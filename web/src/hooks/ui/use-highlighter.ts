"use client";

import { useEffect, useMemo, useState } from "react";

import type { BundledLanguage, BundledTheme, Highlighter } from "shiki";

import { useThemeSync } from "@/hooks/ui/use-theme-sync";

type PlainTextLanguage = "text" | "plaintext" | "txt";

export type HighlighterOptions = {
    language?: BundledLanguage | PlainTextLanguage;
    lightTheme?: BundledTheme;
    darkTheme?: BundledTheme;
};

const DEFAULT_LANGUAGE: PlainTextLanguage = "text";
const DEFAULT_LIGHT_THEME: BundledTheme = "github-light";
const DEFAULT_DARK_THEME: BundledTheme = "github-dark";
const PLAIN_TEXT_LANGUAGES = new Set<string>(["text", "plaintext", "txt"]);

type LoadedHighlighter = {
    highlighter: Highlighter;
    language: BundledLanguage;
    lightTheme: BundledTheme;
    darkTheme: BundledTheme;
};

function isPlainTextLanguage(language: string): language is PlainTextLanguage {
    return PLAIN_TEXT_LANGUAGES.has(language);
}

function escapeHtml(value: string) {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function useHighlighter(
    code: string,
    {
        language = DEFAULT_LANGUAGE,
        lightTheme = DEFAULT_LIGHT_THEME,
        darkTheme = DEFAULT_DARK_THEME,
    }: HighlighterOptions = {},
) {
    const { isDark, isReady } = useThemeSync();
    const [loaded, setLoaded] = useState<LoadedHighlighter | null>(null);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (isPlainTextLanguage(language)) {
            return;
        }

        let cancelled = false;
        let instance: Highlighter | null = null;

        void (async () => {
            try {
                const { createHighlighter } = await import("shiki");
                instance = await createHighlighter({ themes: [lightTheme, darkTheme], langs: [language] });
                if (cancelled) {
                    return;
                }
                setLoaded({ highlighter: instance, language, lightTheme, darkTheme });
                setError(null);
            } catch (cause) {
                if (!cancelled) {
                    setError(cause instanceof Error ? cause : new Error(String(cause)));
                }
            }
        })();

        return () => {
            cancelled = true;
            instance?.dispose();
        };
    }, [language, lightTheme, darkTheme]);

    const html = useMemo(() => {
        if (isPlainTextLanguage(language)) {
            return `<pre class="shiki"><code>${escapeHtml(code)}</code></pre>`;
        }
        if (
            !loaded ||
            !isReady ||
            loaded.language !== language ||
            loaded.lightTheme !== lightTheme ||
            loaded.darkTheme !== darkTheme
        ) {
            return "";
        }
        return loaded.highlighter.codeToHtml(code, { lang: language, theme: isDark ? darkTheme : lightTheme });
    }, [loaded, isReady, isDark, code, language, lightTheme, darkTheme]);

    return { html, loading: !html && !error, error };
}
