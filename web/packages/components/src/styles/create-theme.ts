import { alpha, createTheme, type Theme, type ThemeOptions } from "@mui/material/styles";
import {
  brandPalette,
  fontFamily,
  fontSizes,
  fontWeights,
  lightTokens,
  darkTokens,
  motionDurations,
  motionEasing,
  neutralPalette,
  radii,
  shadowFocus,
  type PudansColorMode,
} from "./tokens";

/**
 * Single source of truth: this file only expresses layout/component contracts.
 * Every colour, radius, duration and easing comes from tokens.ts, so the MUI
 * theme and the CSS-variable layer cannot drift apart.
 */

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

function toHex(channels: [number, number, number]): string {
  return (
    "#" +
    channels
      .map((channel) =>
        Math.round(Math.min(255, Math.max(0, channel)))
          .toString(16)
          .padStart(2, "0"),
      )
      .join("")
  );
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

/** Returns the accent as a CSS "r g b" channel triple for var(--pd-brand-primary). */
export function accentChannels(accentColor?: string): string | null {
  const normalized = normalizeHex(accentColor);
  if (!normalized) {
    return null;
  }
  return channelsOf(normalized).join(" ");
}

const tooltipBackground = "rgba(97, 97, 97, 0.92)";

/**
 * Creates the MUI theme for a colour mode.
 *
 * Extraction provenance: the shape scale (12px), dense menu metrics and the
 * filled-input treatment come from the legacy Cloudreve frontend; the semantic
 * colour/motion scale comes from the pudans token layer. Everything is now
 * consumed through tokens.ts.
 */
export function createPudansTheme(mode: PudansColorMode, accentColor?: string): Theme {
  const tokens = mode === "dark" ? darkTokens : lightTokens;
  const accent = buildAccentColor(accentColor, mode);
  const primary = accent ?? {
    main: tokens.brand,
    light: tokens.brandHover,
    dark: tokens.brandActive,
    contrastText: tokens.brandContrast,
  };
  const secondary = {
    main: mode === "light" ? brandPalette[600] : brandPalette[300],
    light: mode === "light" ? brandPalette[400] : brandPalette[200],
    dark: mode === "light" ? brandPalette[800] : brandPalette[500],
    contrastText: mode === "light" ? neutralPalette[0] : neutralPalette[900],
  };

  const options: ThemeOptions = {
    palette: {
      mode,
      primary,
      secondary,
      background: {
        default: tokens.canvas,
        paper: tokens.surface,
      },
      text: {
        primary: tokens.textPrimary,
        secondary: tokens.textSecondary,
        disabled: tokens.textDisabled,
      },
      divider: tokens.borderDefault,
      success: { main: tokens.success, light: tokens.successSoft, contrastText: "#ffffff" },
      warning: { main: tokens.warning, light: tokens.warningSoft, contrastText: "#ffffff" },
      error: { main: tokens.danger, light: tokens.dangerSoft, contrastText: "#ffffff" },
      info: { main: tokens.info, light: tokens.infoSoft, contrastText: "#ffffff" },
      action: {
        hover: tokens.hover,
        selected: tokens.active,
        disabled: tokens.textDisabled,
        disabledBackground: tokens.muted,
      },
    },
    shape: {
      borderRadius: Number.parseInt(radii.lg, 10),
    },
    breakpoints: {
      values: { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 },
    },
    zIndex: {
      mobileStepper: 1000,
      fab: 1050,
      speedDial: 1050,
      appBar: 1100,
      drawer: 1200,
      modal: 1400,
      snackbar: 1700,
      tooltip: 1800,
    },
    transitions: {
      duration: {
        shortest: motionDurations.fastest,
        shorter: motionDurations.faster,
        short: motionDurations.base,
        standard: motionDurations.slow,
        complex: motionDurations.complex,
        enteringScreen: motionDurations.entering,
        leavingScreen: motionDurations.leaving,
      },
      easing: motionEasing,
      create: (
        props?: string | string[],
        options?: Partial<{ duration: string | number; easing: string; delay: string | number }>,
      ): string => {
        const duration = options?.duration ?? motionDurations.base;
        const easing = options?.easing ?? motionEasing.standard;
        const list = props === undefined ? [] : Array.isArray(props) ? props : [props];
        return list.map((property) => property + " " + duration + "ms " + easing).join(", ");
      },
    },
    typography: {
      fontFamily: fontFamily.sans,
      htmlFontSize: 16,
      fontSize: 14,
      fontWeightLight: 300,
      fontWeightRegular: fontWeights.regular,
      fontWeightMedium: fontWeights.medium,
      fontWeightBold: fontWeights.bold,
      h1: {
        fontSize: fontSizes["3xl"][0],
        lineHeight: fontSizes["3xl"][1].lineHeight,
        fontWeight: fontWeights.bold,
      },
      h2: {
        fontSize: fontSizes["2xl"][0],
        lineHeight: fontSizes["2xl"][1].lineHeight,
        fontWeight: fontWeights.semibold,
      },
      h3: {
        fontSize: fontSizes.xl[0],
        lineHeight: fontSizes.xl[1].lineHeight,
        fontWeight: fontWeights.semibold,
      },
      h4: {
        fontSize: fontSizes.lg[0],
        lineHeight: fontSizes.lg[1].lineHeight,
        fontWeight: fontWeights.semibold,
      },
      h5: {
        fontSize: fontSizes.md[0],
        lineHeight: fontSizes.md[1].lineHeight,
        fontWeight: fontWeights.medium,
      },
      h6: {
        fontSize: fontSizes.base[0],
        lineHeight: fontSizes.base[1].lineHeight,
        fontWeight: fontWeights.semibold,
      },
      subtitle1: {
        fontSize: fontSizes.base[0],
        lineHeight: fontSizes.base[1].lineHeight,
        fontWeight: fontWeights.medium,
      },
      subtitle2: {
        fontSize: fontSizes.sm[0],
        lineHeight: fontSizes.sm[1].lineHeight,
        fontWeight: fontWeights.medium,
      },
      body1: { fontSize: fontSizes.base[0], lineHeight: fontSizes.base[1].lineHeight },
      body2: { fontSize: fontSizes.sm[0], lineHeight: fontSizes.sm[1].lineHeight },
      caption: { fontSize: fontSizes.xs[0], lineHeight: fontSizes.xs[1].lineHeight },
      button: {
        fontSize: fontSizes.base[0],
        lineHeight: fontSizes.base[1].lineHeight,
        fontWeight: fontWeights.medium,
        textTransform: "none",
      },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            overscrollBehavior: "none",
            backgroundColor: tokens.canvas,
            color: tokens.textPrimary,
            fontFamily: fontFamily.sans,
          },
          "::selection": {
            backgroundColor: tokens.brandSoft,
            color: tokens.textPrimary,
          },
        },
      },
      MuiTooltip: {
        defaultProps: { enterDelay: 500 },
        styleOverrides: {
          tooltip: {
            borderRadius: Number.parseInt(radii.xs, 10),
            backgroundColor: tooltipBackground,
            color: "#ffffff",
            boxShadow: "none",
            fontSize: Number.parseInt(fontSizes.xs[0], 10),
            lineHeight: fontSizes.xs[1].lineHeight,
            padding: "6px 9px",
          },
          arrow: { color: tooltipBackground },
        },
      },
      MuiCheckbox: {
        defaultProps: { color: "primary" },
        styleOverrides: { root: { borderRadius: Number.parseInt(radii.xs, 10) } },
      },
      MuiSwitch: { defaultProps: { color: "primary" } },
      MuiTabs: {
        styleOverrides: {
          root: {
            minHeight: 40,
            "& .MuiTabs-indicator": { display: "none" },
            "& .MuiTabs-flexContainer": { alignItems: "center", gap: 8 },
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
            fontSize: Number.parseInt(fontSizes.base[0], 10),
            fontWeight: fontWeights.medium,
            textTransform: "none",
          },
        },
      },
      MuiPaginationItem: {
        styleOverrides: {
          root: {
            borderRadius: Number.parseInt(radii.md, 10),
            "&.Mui-selected": {
              backgroundColor: primary.main,
              color: primary.contrastText,
            },
            "&.Mui-selected:hover": { backgroundColor: primary.dark },
          },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: { borderRadius: Number.parseInt(radii.lg, 10), boxShadow: "none" },
        },
      },
      MuiTableHead: { styleOverrides: { root: { backgroundColor: "transparent" } } },
      MuiTableCell: {
        styleOverrides: {
          root: { borderBottom: "1px solid " + tokens.borderDefault },
          head: {
            color: tokens.textSecondary,
            fontSize: Number.parseInt(fontSizes.xs[0], 10),
            fontWeight: fontWeights.semibold,
          },
        },
      },
      MuiToggleButton: { styleOverrides: { root: { textTransform: "none" } } },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: {
            minHeight: 36,
            borderRadius: Number.parseInt(radii.lg, 10),
            fontWeight: fontWeights.medium,
            textTransform: "none",
            boxShadow: "none",
          },
        },
      },
      MuiPaper: { styleOverrides: { root: { backgroundImage: "none" } } },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: Number.parseInt(radii.lg, 10),
            border: "1px solid " + tokens.borderDefault,
            boxShadow: "none",
            backgroundImage: "none",
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: Number.parseInt(radii.lg, 10),
            backgroundImage: "none",
            border: "1px solid " + tokens.borderDefault,
          },
        },
      },
      MuiDialogContent: { styleOverrides: { root: { paddingTop: 0 } } },
      MuiMenu: {
        defaultProps: { slotProps: { paper: { elevation: 3 } } },
        styleOverrides: {
          paper: { borderRadius: Number.parseInt(radii.md, 10) },
          list: { padding: "4px 0" },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            borderRadius: Number.parseInt(radii.md, 10),
            margin: "0px 4px",
            paddingLeft: 8,
            paddingRight: 8,
          },
        },
      },
      MuiListItemButton: {
        styleOverrides: { root: { borderRadius: Number.parseInt(radii.lg, 10) } },
      },
      MuiSkeleton: { defaultProps: { animation: "wave" } },
      MuiFilledInput: {
        styleOverrides: {
          root: {
            borderRadius: Number.parseInt(radii.lg, 10),
            backgroundColor: alpha(tokens.textPrimary, 0.06),
            "&::before, &::after": { borderBottom: "none" },
            "&:hover:not(.Mui-disabled, .Mui-error):before": { borderBottom: "none" },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: Number.parseInt(radii.lg, 10),
            backgroundColor: "transparent",
            "& .MuiOutlinedInput-notchedOutline": {
              borderColor: alpha(tokens.textPrimary, 0.23),
            },
            "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: tokens.textPrimary },
            "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
              borderColor: primary.main,
              borderWidth: 2,
            },
          },
        },
      },
      MuiLink: {
        styleOverrides: { root: { color: primary.main } },
      },
    },
  };

  return createTheme(options);
}

export { lightTokens, darkTokens } from "./tokens";
export { shadowFocus };
