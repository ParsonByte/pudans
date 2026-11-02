import type { Config } from "tailwindcss";
import {
  brandPalette,
  controlHeights,
  fontFamily,
  fontSizes,
  motion,
  neutralPalette,
  radii,
  shadows,
  spacing,
} from "./tokens";

const channel = (variable: string): string => `rgb(var(${variable}) / <alpha-value>)`;

const paletteToColors = (prefix: string, palette: Record<string, string>): Record<string, string> =>
  Object.fromEntries(Object.keys(palette).map((step) => [step, channel(`--pd-${prefix}-${step}`)]));

export default {
  darkMode: "class",
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: paletteToColors("brand", brandPalette),
        neutral: paletteToColors("neutral", neutralPalette),
        primary: {
          DEFAULT: channel("--pd-brand-primary"),
          hover: channel("--pd-brand-hover"),
          active: channel("--pd-brand-active"),
          foreground: channel("--pd-brand-contrast"),
        },
        canvas: channel("--pd-bg-canvas"),
        surface: channel("--pd-bg-surface"),
        elevated: channel("--pd-bg-elevated"),
        subtle: channel("--pd-bg-subtle"),
        muted: channel("--pd-bg-muted"),
        hover: "var(--pd-bg-hover)",
        active: "var(--pd-bg-active)",
        foreground: {
          DEFAULT: channel("--pd-text-primary"),
          primary: channel("--pd-text-primary"),
          secondary: channel("--pd-text-secondary"),
          tertiary: channel("--pd-text-tertiary"),
          disabled: channel("--pd-text-disabled"),
          inverse: channel("--pd-text-inverse"),
        },
        border: {
          subtle: "var(--pd-border-subtle)",
          DEFAULT: "var(--pd-border-default)",
          strong: "var(--pd-border-strong)",
        },
        success: {
          DEFAULT: channel("--pd-state-success"),
          soft: channel("--pd-state-success-soft"),
        },
        warning: {
          DEFAULT: channel("--pd-state-warning"),
          soft: channel("--pd-state-warning-soft"),
        },
        danger: {
          DEFAULT: channel("--pd-state-danger"),
          soft: channel("--pd-state-danger-soft"),
        },
        info: {
          DEFAULT: channel("--pd-state-info"),
          soft: channel("--pd-state-info-soft"),
        },
        chart: {
          "1": channel("--pd-chart-1"),
          "2": channel("--pd-chart-2"),
          "3": channel("--pd-chart-3"),
          "4": channel("--pd-chart-4"),
          "5": channel("--pd-chart-5"),
          "6": channel("--pd-chart-6"),
        },
      },
      borderRadius: { ...radii },
      boxShadow: { ...shadows },
      spacing: {
        gutter: `${spacing.gutter}px`,
        section: `${spacing.section}px`,
        sidebar: `${spacing.sidebar}px`,
        header: `${spacing.header}px`,
        "control-xs": `${controlHeights.xs}px`,
        "control-sm": `${controlHeights.sm}px`,
        "control-md": `${controlHeights.md}px`,
        "control-lg": `${controlHeights.lg}px`,
        "control-xl": `${controlHeights.xl}px`,
      },
      fontFamily: {
        sans: [`var(--pd-font-sans)`],
        mono: [`var(--pd-font-mono)`],
      },
      fontSize: { ...fontSizes },
      transitionDuration: {
        fast: motion.fast,
        base: motion.base,
        slow: motion.slow,
      },
      transitionTimingFunction: {
        standard: motion.easing,
        emphasized: motion.easingEmphasized,
      },
      backgroundImage: {
        "brand-gradient":
          "linear-gradient(45deg, rgb(var(--pd-brand-primary)) 30%, rgb(var(--pd-brand-400)) 90%)",
      },
      keyframes: {
        "pd-fade-in": {
          from: { opacity: "0" },
          to: { opacity: "1" },
        },
        "pd-scale-in": {
          from: { opacity: "0", transform: "scale(0.96)" },
          to: { opacity: "1", transform: "scale(1)" },
        },
        "pd-slide-up": {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "pd-spin": {
          to: { transform: "rotate(360deg)" },
        },
        "pd-shimmer": {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        "fade-in": `pd-fade-in ${motion.base} ${motion.easing}`,
        "scale-in": `pd-scale-in ${motion.base} ${motion.easingEmphasized}`,
        "slide-up": `pd-slide-up ${motion.slow} ${motion.easingEmphasized}`,
        spin: "pd-spin 700ms linear infinite",
        shimmer: "pd-shimmer 1.6s linear infinite",
      },
      zIndex: {
        header: "100",
        sidebar: "200",
        overlay: "900",
        modal: "1000",
        toast: "1100",
      },
      maxWidth: {
        container: `${spacing.container}px`,
      },
    },
  },
  plugins: [],
} satisfies Config;
