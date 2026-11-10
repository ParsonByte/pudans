import { alpha, createTheme, type Theme } from "@mui/material/styles";
import type { PudansColorMode } from "./tokens";

const fontFamily = '"Roboto", "Helvetica", "Arial", sans-serif';

const cloudrevePalette = {
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
    primary: {
      main: "#90caf9",
      light: "#e3f2fd",
      dark: "#42a5f5",
      contrastText: "rgba(0, 0, 0, 0.87)",
    },
    secondary: {
      main: "#ce93d8",
      light: "#f3e5f5",
      dark: "#ab47bc",
      contrastText: "rgba(0, 0, 0, 0.87)",
    },
    background: { default: "#121212", paper: "#121212" },
    text: {
      primary: "rgba(255, 255, 255, 0.87)",
      secondary: "rgba(255, 255, 255, 0.60)",
      disabled: "rgba(255, 255, 255, 0.38)",
    },
    divider: "rgba(255, 255, 255, 0.12)",
  },
} as const;

function normalizeHex(value: string | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  const short = /^#([0-9a-f]{3})$/i.exec(trimmed);
  if (short) {
    const [r, g, b] = short[1].split("");
    return `#${r}${r}${g}${g}${b}${b}`.toLowerCase();
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

function toHex(channels: [number, number, number]): string {
  return `#${channels
    .map((channel) =>
      Math.round(Math.min(255, Math.max(0, channel)))
        .toString(16)
        .padStart(2, "0"),
    )
    .join("")}`;
}

function mixHex(source: string, target: string, weight: number): string {
  const a = channelsOf(source);
  const b = channelsOf(target);
  return toHex([
    a[0] + (b[0] - a[0]) * weight,
    a[1] + (b[1] - a[1]) * weight,
    a[2] + (b[2] - a[2]) * weight,
  ]);
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
    light: mixHex(normalized, "#ffffff", mode === "light" ? 0.3 : 0.18),
    dark: mixHex(normalized, "#000000", mode === "light" ? 0.22 : 0.08),
    contrastText: contrastTextFor(normalized),
  };
}

/** Returns the accent as a CSS `r g b` channel triple for var(--pd-brand-primary). */
export function accentChannels(accentColor?: string): string | null {
  const normalized = normalizeHex(accentColor);
  if (!normalized) {
    return null;
  }
  return channelsOf(normalized).join(" ");
}

/**
 * Cloudreve theme extraction:
 * - palette defaults from cloudreve/inventory/setting.go (#1976d2 light, #90caf9 dark)
 * - optional accent color derived into light/dark/contrast steps
 * - 12px global shape radius
 * - MUI default typography/shadows
 * - Cloudreve App.tsx component overrides (buttons, menus, dialogs, filled input)
 */
export function createPudansTheme(mode: PudansColorMode, accentColor?: string): Theme {
  const palette = cloudrevePalette[mode];
  const accent = buildAccentColor(accentColor, mode);

  return createTheme({
    palette: {
      mode,
      primary: accent ?? palette.primary,
      secondary: palette.secondary,
      background: palette.background,
      text: palette.text,
      divider: palette.divider,
    },
    shape: {
      borderRadius: 12,
    },
    typography: {
      fontFamily,
      htmlFontSize: 16,
      fontSize: 14,
      fontWeightLight: 300,
      fontWeightRegular: 400,
      fontWeightMedium: 500,
      fontWeightBold: 700,
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            overscrollBehavior: "none",
            backgroundColor: palette.background.default,
            color: palette.text.primary,
            fontFamily,
          },
        },
      },
      MuiTooltip: {
        defaultProps: {
          enterDelay: 500,
        },
        styleOverrides: {
          tooltip: {
            borderRadius: 4,
            backgroundColor: "rgba(97, 97, 97, 0.92)",
            color: "#ffffff",
            boxShadow: "none",
            fontSize: 12,
            lineHeight: "18px",
            padding: "6px 9px",
          },
          arrow: {
            color: "rgba(97, 97, 97, 0.92)",
          },
        },
      },
      MuiCheckbox: {
        defaultProps: {
          color: "primary",
        },
        styleOverrides: {
          root: {
            borderRadius: 4,
          },
        },
      },
      MuiSwitch: {
        defaultProps: {
          color: "primary",
        },
      },
      MuiTabs: {
        styleOverrides: {
          root: {
            minHeight: 40,
            "& .MuiTabs-indicator": {
              display: "none",
            },
            "& .MuiTabs-flexContainer": {
              alignItems: "center",
              gap: 8,
            },
          },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            minHeight: 36,
            minWidth: 0,
            padding: "6px 16px",
            borderRadius: 9999,
            fontSize: 14,
            fontWeight: 500,
            textTransform: "none",
          },
        },
      },
      MuiPaginationItem: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            "&.Mui-selected": {
              backgroundColor: palette.primary.main,
              color: palette.primary.contrastText,
            },
            "&.Mui-selected:hover": {
              backgroundColor: palette.primary.dark,
            },
          },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            boxShadow: "none",
          },
        },
      },
      MuiTableHead: {
        styleOverrides: {
          root: {
            backgroundColor: "transparent",
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderBottom: `1px solid ${palette.divider}`,
          },
          head: {
            color: palette.text.secondary,
            fontSize: 12,
            fontWeight: 600,
          },
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
            minHeight: 36,
            borderRadius: 12,
            fontWeight: 500,
            textTransform: "none",
            boxShadow: "none",
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: "none",
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            border: `1px solid ${palette.divider}`,
            boxShadow: "none",
            backgroundImage: "none",
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 12,
            backgroundImage: "none",
            border: `1px solid ${palette.divider}`,
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
            borderRadius: 8,
          },
          list: {
            padding: "4px 0",
          },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            margin: "0px 4px",
            paddingLeft: 8,
            paddingRight: 8,
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
      MuiSkeleton: {
        defaultProps: {
          animation: "wave",
        },
      },
      MuiFilledInput: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            backgroundColor: alpha(palette.text.primary, 0.06),
            "&::before, &::after": {
              borderBottom: "none",
            },
            "&:hover:not(.Mui-disabled, .Mui-error):before": {
              borderBottom: "none",
            },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            backgroundColor: "transparent",
            "& .MuiOutlinedInput-notchedOutline": {
              borderColor: alpha(palette.text.primary, 0.23),
            },
            "&:hover .MuiOutlinedInput-notchedOutline": {
              borderColor: palette.text.primary,
            },
            "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
              borderColor: palette.primary.main,
              borderWidth: 2,
            },
          },
        },
      },
    },
  });
}

export { lightTokens, darkTokens } from "./tokens";
