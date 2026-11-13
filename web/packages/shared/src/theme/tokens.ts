export type PudansColorMode = "light" | "dark";
export type PudansColorModePreference = PudansColorMode | "system";

/**
 * Brand ramp. 500 is the light-mode primary, 450 the dark-mode primary.
 * Values are channel-safe hex strings; CSS variables mirror them as RGB triples.
 */
export const brandPalette = {
  50: "#e3f2fd",
  100: "#bbdefb",
  200: "#90caf9",
  300: "#64b5f6",
  400: "#42a5f5",
  450: "#2196f3",
  500: "#1976d2",
  600: "#1565c0",
  700: "#114a9e",
  800: "#0d47a1",
  900: "#0a3a7a",
} as const;

/** Cool neutral ramp shared by every surface, border and text token. */
export const neutralPalette = {
  0: "#ffffff",
  50: "#fafafa",
  60: "#f5f5f5",
  75: "#f0f0f0",
  100: "#e0e0e0",
  150: "#d5d5d5",
  200: "#bdbdbd",
  300: "#9e9e9e",
  400: "#757575",
  600: "#616161",
  700: "#424242",
  750: "#303030",
  800: "#212121",
  850: "#1e1e1e",
  900: "#121212",
  950: "#0a0a0a",
  1000: "#121212",
} as const;

export interface PudansModeTokens {
  canvas: string;
  surface: string;
  elevated: string;
  subtle: string;
  muted: string;
  hover: string;
  active: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  textDisabled: string;
  textInverse: string;
  brand: string;
  brandHover: string;
  brandActive: string;
  brandContrast: string;
  brandSoft: string;
  borderSubtle: string;
  borderDefault: string;
  borderStrong: string;
  focusRing: string;
  success: string;
  successSoft: string;
  warning: string;
  warningSoft: string;
  danger: string;
  dangerSoft: string;
  info: string;
  infoSoft: string;
}

export const lightTokens: PudansModeTokens = {
  canvas: neutralPalette[50],
  surface: neutralPalette[0],
  elevated: neutralPalette[0],
  subtle: neutralPalette[60],
  muted: neutralPalette[100],
  hover: "rgba(0, 0, 0, 0.04)",
  active: "rgba(0, 0, 0, 0.08)",
  textPrimary: "#000000",
  textSecondary: "#666666",
  textTertiary: "#8a8a8a",
  textDisabled: "#bdbdbd",
  textInverse: neutralPalette[0],
  brand: brandPalette[500],
  brandHover: brandPalette[400],
  brandActive: brandPalette[600],
  brandContrast: neutralPalette[0],
  brandSoft: brandPalette[50],
  borderSubtle: "rgba(0, 0, 0, 0.08)",
  borderDefault: "rgba(0, 0, 0, 0.12)",
  borderStrong: "rgba(0, 0, 0, 0.23)",
  focusRing: "rgba(25, 118, 210, 0.35)",
  success: "#2e7d32",
  successSoft: "#e8f5e9",
  warning: "#ed6c02",
  warningSoft: "#fff4e5",
  danger: "#d32f2f",
  dangerSoft: "rgba(211, 47, 47, 0.08)",
  info: "#0288d1",
  infoSoft: "#e5f4fd",
};

export const darkTokens: PudansModeTokens = {
  canvas: neutralPalette[900],
  surface: neutralPalette[900],
  elevated: neutralPalette[850],
  subtle: neutralPalette[850],
  muted: neutralPalette[800],
  hover: "rgba(255, 255, 255, 0.08)",
  active: "rgba(255, 255, 255, 0.16)",
  textPrimary: "#ffffff",
  textSecondary: "#999999",
  textTertiary: "#b3b3b3",
  textDisabled: "#616161",
  textInverse: neutralPalette[900],
  brand: brandPalette[200],
  brandHover: brandPalette[100],
  brandActive: brandPalette[400],
  brandContrast: neutralPalette[900],
  brandSoft: "#1e2b38",
  borderSubtle: "rgba(255, 255, 255, 0.08)",
  borderDefault: "rgba(255, 255, 255, 0.12)",
  borderStrong: "rgba(255, 255, 255, 0.23)",
  focusRing: "rgba(144, 202, 249, 0.45)",
  success: "#66bb6a",
  successSoft: "rgba(102, 187, 106, 0.12)",
  warning: "#ffa726",
  warningSoft: "rgba(255, 167, 38, 0.12)",
  danger: "#f44336",
  dangerSoft: "rgba(244, 67, 54, 0.15)",
  info: "#29b6f6",
  infoSoft: "rgba(41, 182, 246, 0.12)",
};

export const modeTokens: Record<PudansColorMode, PudansModeTokens> = {
  light: lightTokens,
  dark: darkTokens,
};

export const radii = {
  none: "0px",
  xs: "4px",
  sm: "6px",
  md: "8px",
  lg: "12px",
  xl: "16px",
  "2xl": "20px",
  full: "9999px",
} as const;

export const shadows = {
  xs: "0 1px 2px 0 rgba(15, 17, 21, 0.04)",
  sm: "0 1px 3px 0 rgba(15, 17, 21, 0.08), 0 1px 2px -1px rgba(15, 17, 21, 0.08)",
  md: "0 4px 12px -2px rgba(15, 17, 21, 0.10), 0 2px 4px -2px rgba(15, 17, 21, 0.06)",
  lg: "0 0 1px 0 rgba(0, 0, 0, 0.20), 0 0 4px 0 rgba(0, 0, 0, 0.02), 0 12px 32px 0 rgba(0, 0, 0, 0.08)",
  xl: "0 0 1px 0 rgba(0, 0, 0, 0.24), 0 20px 48px 0 rgba(0, 0, 0, 0.14)",
  focus: "0 0 0 3px rgba(25, 118, 210, 0.35)",
} as const;

export const shadowFocus = shadows.focus;

/** Icon glyph sizes used by control internals (checkbox, tree, avatars). */
export const iconSizes = {
  xs: 14,
  sm: 16,
  md: 20,
  lg: 24,
  xl: 28,
} as const;

/** Media/glass/scrim effects used by surfaces layered over video or imagery. */
/** Preset accent colours offered by the console appearance settings. */
export const accentPresets = [
  "#1976d2",
  "#0f766e",
  "#7c3aed",
  "#db2777",
  "#ea580c",
  "#475569",
] as const;

export const effectTokens = {
  mediaText: "#ffffff",
  mediaTextSecondary: "rgba(255, 255, 255, 0.72)",
  mediaTextTertiary: "rgba(255, 255, 255, 0.5)",
  scrimStrong: "rgba(0, 0, 0, 0.5)",
  scrimMedium: "rgba(0, 0, 0, 0.36)",
  scrimSoft: "rgba(0, 0, 0, 0.22)",
  glassSurface: "rgba(255, 255, 255, 0.1)",
  glassBorder: "rgba(255, 255, 255, 0.2)",
  glassSurfaceSoft: "rgba(255, 255, 255, 0.08)",
  glassBorderSoft: "rgba(255, 255, 255, 0.14)",
  navVeil: "rgba(255, 255, 255, 0.88)",
  /** Always-white surface required for QR code scanning. */
  qrSurface: "#ffffff",
} as const;

export const borderWidths = {
  none: 0,
  thin: 1,
  thick: 2,
} as const;

/** deepseek-chat derived control heights: xs 28 / sm 32 / md 36 / lg 40 / xl 44. */
export const controlHeights = {
  xs: 28,
  sm: 32,
  md: 36,
  lg: 40,
  xl: 44,
} as const;

export const fontWeights = {
  regular: 400,
  medium: 500,
  semibold: 600,
  bold: 700,
} as const;

export const fontSizes = {
  xs: ["12px", { lineHeight: "18px" }],
  sm: ["13px", { lineHeight: "20px" }],
  base: ["14px", { lineHeight: "22px" }],
  md: ["15px", { lineHeight: "24px" }],
  lg: ["16px", { lineHeight: "26px" }],
  xl: ["20px", { lineHeight: "30px" }],
  "2xl": ["24px", { lineHeight: "34px" }],
  "3xl": ["30px", { lineHeight: "40px" }],
} as const;

export const spacing = {
  unit: 4,
  base: 4,
  gutter: 24,
  section: 48,
  sidebar: 248,
  sidebarCollapsed: 72,
  header: 56,
  container: 1440,
} as const;

export const fontFamily = {
  sans: '"Roboto", "Helvetica", "Arial", sans-serif',
  mono: 'Menlo, Monaco, Consolas, "Cascadia Mono", "Ubuntu Mono", "JetBrains Mono", "Fira Code", "Courier New", monospace',
} as const;

/**
 * Motion scale. Durations are aligned with the legacy legacy/Material scale
 * (150/200/250/300/375ms, 225ms in / 195ms out for screen transitions).
 */
export const motionDurations = {
  instant: 100,
  fastest: 150,
  faster: 200,
  fast: 150,
  base: 250,
  slow: 300,
  slower: 375,
  complex: 375,
  entering: 225,
  leaving: 195,
  skeleton: 1400,
  spin: 900,
  /** Autofill background transition hack; tokenized instead of an inline literal. */
  autofill: 9999,
} as const;

export const motionEasing = {
  standard: "cubic-bezier(0.4, 0, 0.2, 1)",
  in: "cubic-bezier(0.4, 0, 1, 1)",
  out: "cubic-bezier(0, 0, 0.2, 1)",
  sharp: "cubic-bezier(0.4, 0, 0.6, 1)",
  emphasized: "cubic-bezier(0.2, 0, 0, 1)",
} as const;

export const motion = {
  fast: "150ms",
  base: "250ms",
  slow: "300ms",
  shortest: "150ms",
  shorter: "200ms",
  short: "250ms",
  standard: "300ms",
  complex: "375ms",
  entering: "225ms",
  leaving: "195ms",
  easing: motionEasing.standard,
  easingIn: motionEasing.in,
  easingOut: motionEasing.out,
  easingSharp: motionEasing.sharp,
  easingEmphasized: motionEasing.emphasized,
} as const;

export const zIndex = {
  base: 0,
  sticky: 100,
  mobileStepper: 1000,
  fab: 1050,
  speedDial: 1050,
  appBar: 1100,
  drawer: 1200,
  raised: 1,
  shell: 40,
  shellRaised: 50,
  dropdown: 1000,
  overlay: 1300,
  modal: 1400,
  popover: 1500,
  toast: 1700,
  tooltip: 1800,
} as const;

export const opacity = {
  disabled: 0.45,
  muted: 0.72,
  hidden: 0,
  full: 1,
  backdrop: 0.5,
  hover: 0.04,
  active: 0.08,
} as const;

export const breakpoints = {
  sm: 600,
  md: 900,
  lg: 1200,
  xl: 1536,
} as const;

/** Composed CSS transition shorthands; every component should use one of these. */
export const transitions = {
  colors:
    "color 150ms cubic-bezier(0.4, 0, 0.2, 1), background-color 150ms cubic-bezier(0.4, 0, 0.2, 1), border-color 150ms cubic-bezier(0.4, 0, 0.2, 1), fill 150ms cubic-bezier(0.4, 0, 0.2, 1)",
  transform: "transform 150ms cubic-bezier(0.4, 0, 0.2, 1)",
  opacity: "opacity 200ms cubic-bezier(0.4, 0, 0.2, 1)",
  shadow: "box-shadow 250ms cubic-bezier(0.4, 0, 0.2, 1)",
  all: "all 250ms cubic-bezier(0.4, 0, 0.2, 1)",
} as const;

/** Interaction states shared by every component (hover lift, press scale, focus ring). */
export const interaction = {
  hoverLift: "-1px",
  pressScale: "0.985",
  focusRingWidth: 3,
  focusRingOffset: 2,
  disabledOpacity: opacity.disabled,
} as const;
