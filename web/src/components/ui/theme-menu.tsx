"use client";

import type { CSSProperties } from "react";

import { flushSync } from "react-dom";
import { MoonIcon, SunnyIcon } from "tdesign-icons-react";
import { Button } from "tdesign-react";

import { useThemeSync } from "@/hooks/ui/use-theme-sync";

type Theme = "light" | "dark";

type ViewTransitionDocument = Document & {
    startViewTransition?: (update: () => void) => unknown;
};

type ThemeMenuProps = {
    buttonClassName?: string;
    buttonStyle?: CSSProperties;
};

function applyDocumentTheme(theme: Theme) {
    const root = document.documentElement;

    root.classList.toggle("dark", theme === "dark");
    root.style.colorScheme = theme;
}

export function ThemeMenu({ buttonClassName, buttonStyle }: ThemeMenuProps) {
    const { isDark, isReady, setTheme } = useThemeSync();
    const activeTheme = isReady ? (isDark ? "dark" : "light") : undefined;
    const className = ["theme-menu", buttonClassName].filter(Boolean).join(" ");
    const selectTheme = (theme: Theme) => {
        if (theme === activeTheme) {
            return;
        }

        const updateTheme = () => {
            applyDocumentTheme(theme);
            flushSync(() => setTheme(theme));
        };
        const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        const transitionDocument = document as ViewTransitionDocument;

        if (!transitionDocument.startViewTransition || prefersReducedMotion) {
            updateTheme();
            return;
        }

        transitionDocument.startViewTransition(updateTheme);
    };

    return (
        <div aria-label="切换主题" className={className} role="group" style={buttonStyle}>
            <span aria-hidden className="theme-menu__thumb" data-theme={activeTheme} />
            <Button
                aria-label="浅色"
                aria-pressed={activeTheme === "light"}
                className="theme-menu__option theme-menu__option--light"
                ghost
                icon={<SunnyIcon />}
                onClick={() => selectTheme("light")}
                shape="square"
                type="button"
                variant="text"
            />
            <Button
                aria-label="深色"
                aria-pressed={activeTheme === "dark"}
                className="theme-menu__option theme-menu__option--dark"
                ghost
                icon={<MoonIcon />}
                onClick={() => selectTheme("dark")}
                shape="square"
                type="button"
                variant="text"
            />
        </div>
    );
}
