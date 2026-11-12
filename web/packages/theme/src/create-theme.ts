import { createElement } from "react";
import { Grow, SvgIcon, type SvgIconProps } from "@mui/material";
import { createTheme, type Theme, type ThemeOptions } from "@mui/material/styles";
import type { PudansColorMode } from "./tokens";

/**
 * Legacy-faithful MUI theme derivation.
 *
 * Visual baseline: legacy/frontend (MUI 6 defaults) + the overrides that the
 * legacy App.tsx applied through applyThemeWithOverrides:
 * - shape.borderRadius = 12
 * - MuiButton / MuiTab / MuiToggleButton: textTransform none + disableElevation
 * - MuiMenu paper 8px radius + elevation 3 + list padding 4px 0
 * - MuiMenuItem 8px radius, margin 0 4px, padding 8px
 * - MuiListItemButton 12px radius
 * - MuiDialog Grow transition + DialogContent paddingTop 0
 * - MuiFilledInput 12px radius with underline removed
 * - MuiTooltip enterDelay 500, MuiSkeleton wave, CssBaseline overscroll none
 * - MuiAlert warning icon mapping
 *
 * Palette defaults come from the legacy theme options:
 * light primary #1976d2 (light #42a5f5 / dark #1565c0), dark primary #90caf9
 * (light #e3f2fd / dark #42a5f5), secondary #9c27b0 / #ce93d8.
 */

const LEGACY_PALETTE = {
  light: {
    primary: { main: "#1976d2", light: "#42a5f5", dark: "#1565c0", contrastText: "#ffffff" },
    secondary: { main: "#9c27b0", light: "#ba68c8", dark: "#7b1fa2", contrastText: "#ffffff" },
    background: { default: "#fafafa", paper: "#ffffff" },
    text: {
      primary: "rgba(0, 0, 0, 0.87)",
      secondary: "rgba(0, 0, 0, 0.60)",
      disabled: "rgba(0, 0, 0, 0.38)",
    },
    divider: "rgba(0, 0, 0, 0.12)",
  },
  dark: {
    primary: { main: "#90caf9", light: "#e3f2fd", dark: "#42a5f5", contrastText: "rgba(0, 0, 0, 0.87)" },
    secondary: { main: "#ce93d8", light: "#f3e5f5", dark: "#ab47bc", contrastText: "rgba(0, 0, 0, 0.87)" },
    background: { default: "#121212", paper: "#121212" },
    text: {
      primary: "rgba(255, 255, 255, 0.87)",
      secondary: "rgba(255, 255, 255, 0.60)",
      disabled: "rgba(255, 255, 255, 0.38)",
    },
    divider: "rgba(255, 255, 255, 0.12)",
  },
} as const;

const LEGACY_WARNING_PATH =
  "M12 2L1 21h22L12 2zm0 3.5L20 19H4l8-13.5zM11 10v4h2v-4h-2zm0 5v2h2v-2h-2z";

function warningIcon(props: SvgIconProps) {
  return createElement(SvgIcon, props, createElement("path", { d: LEGACY_WARNING_PATH }));
}

function normalizeHex(value: string | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  const short = /^#([0-9a-f]{3})$/i.exec(trimmed);
  if (short) {
    const [r, g, b] = short[1].split("");
    return "#" + r + r + g + g + b + b;
  }
  return /^#[0-9a-f]{6}$/i.test(trimmed) ? trimmed.toLowerCase() : null;
}

function channelsOf(hex: string): [number, number, number] {
  return [
    Number.parseInt(hex.slice(1, 3), 16),
    Number.parseInt(hex.slice(3, 5), 16),
    Number.parseInt(hex.slice(5, 7), 16),
  ];
}

function mixHex(source: string, target: string, weight: number): string {
  const a = channelsOf(source);
  const b = channelsOf(target);
  return (
    "#" +
    a
      .map((channel, index) => {
        const value = channel + (b[index] - channel) * weight;
        return Math.round(Math.min(255, Math.max(0, value))).toString(16).padStart(2, "0");
      })
      .join("")
  );
}

function contrastTextFor(hex: string): string {
  const [r, g, b] = channelsOf(hex);
  const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  return luminance > 0.62 ? "#000000" : "#ffffff";
}

export interface PudansAccentColor {
  main: string;
  light: string;
  dark: string;
  contrastText: string;
}

/**
 * Legacy-friendly accent derivation. The legacy theme_options only provided
 * palette.primary.main for custom accents and let MUI derive the remaining
 * shades; we mimic that with a 20% white / 30% mix (MUI lighten/darken-like).
 */
export function buildAccentColor(
  accentColor?: string,
  mode: PudansColorMode = "light",
): PudansAccentColor | null {
  const normalized = normalizeHex(accentColor);
  if (!normalized) {
    return null;
  }
  return {
    main: normalized,
    light: mixHex(normalized, "#ffffff", 0.2),
    dark: mode === "light" ? mixHex(normalized, "#000000", 0.3) : mixHex(normalized, "#000000", 0.2),
    contrastText: contrastTextFor(normalized),
  };
}

/** Returns the accent as a CSS "r g b" channel triple for var(--pd-brand-primary). */
export function accentChannels(accentColor?: string): string | null {
  const normalized = normalizeHex(accentColor);
  if (!normalized) {
    return null;
  }
  return channelsOf(normalized).join(" ");
}

export function createPudansTheme(mode: PudansColorMode, accentColor?: string): Theme {
  const legacy = LEGACY_PALETTE[mode];
  const accent = buildAccentColor(accentColor, mode);
  const primary = accent ?? legacy.primary;

  const options: ThemeOptions = {
    palette: {
      mode,
      primary,
      secondary: legacy.secondary,
      background: legacy.background,
      text: legacy.text,
      divider: legacy.divider,
    },
    shape: {
      borderRadius: 12,
    },
    typography: {
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            overscrollBehavior: "none",
          },
        },
      },
      MuiTooltip: {
        defaultProps: {
          enterDelay: 500,
        },
      },
      MuiToggleButton: {
        styleOverrides: {
          root: {
            textTransform: "none",
          },
        },
      },
      MuiButton: {
        defaultProps: {
          disableElevation: true,
        },
        styleOverrides: {
          root: {
            textTransform: "none",
          },
        },
      },
      MuiAlert: {
        defaultProps: {
          iconMapping: {
            warning: warningIcon({ color: "inherit" }),
          },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: 12,
          },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            textTransform: "none",
          },
        },
      },
      MuiSkeleton: {
        defaultProps: {
          animation: "wave",
        },
      },
      MuiMenu: {
        defaultProps: {
          slotProps: {
            paper: {
              elevation: 3,
            },
          },
        },
        styleOverrides: {
          paper: {
            borderRadius: "8px",
          },
          list: {
            padding: "4px 0",
          },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            borderRadius: "8px",
            margin: "0px 4px",
            paddingLeft: 8,
            paddingRight: 8,
          },
        },
      },
      MuiDialogContent: {
        styleOverrides: {
          root: {
            paddingTop: 0,
          },
        },
      },
      MuiDialog: {
        defaultProps: {
          slots: {
            transition: Grow,
          },
        },
        styleOverrides: {
          paper: {
            borderRadius: 12,
          },
        },
      },
      MuiFilledInput: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            "&::before, &::after": {
              borderBottom: "none",
            },
            "&:hover:not(.Mui-disabled, .Mui-error):before": {
              borderBottom: "none",
            },
          },
        },
      },
    },
  };

  return createTheme(options);
}

export { lightTokens, darkTokens } from "./tokens";
