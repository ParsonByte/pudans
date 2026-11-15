import { useState, type CSSProperties, type ReactNode } from "react";
import {
  Box,
  IconButton,
  Popover,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  type SxProps,
  type Theme,
} from "@mui/material";
import { usePudansTheme } from "@pudans/shared/theme";
import type { PudansColorModePreference } from "@pudans/shared/theme";
import { Icon } from "../icon";

export interface ThemeSwitchLabels {
  light: ReactNode;
  system: ReactNode;
  dark: ReactNode;
}

export interface ThemeSwitchGroupProps {
  value: PudansColorModePreference;
  onChange: (preference: PudansColorModePreference) => void;
  labels?: ThemeSwitchLabels;
  showLabels?: boolean;
  size?: "sm" | "md";
  disabled?: boolean;
  className?: string;
  sx?: SxProps<Theme>;
}

export interface ThemeSwitchProps {
  labels?: ThemeSwitchLabels;
  showLabels?: boolean;
  size?: "sm" | "md";
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
  style?: CSSProperties;
  sx?: SxProps<Theme>;
  /** "popover" = legacy NavBar DarkThemeSwitcher (icon trigger + popover). */
  variant?: "popover" | "inline";
}

const DEFAULT_LABELS: ThemeSwitchLabels = { light: "Light", system: "System", dark: "Dark" };

const OPTIONS: Array<{ value: PudansColorModePreference; icon: string }> = [
  { value: "light", icon: "pd:Sunny" },
  { value: "system", icon: "pd:SunWithTime" },
  { value: "dark", icon: "pd:Moon" },
];

function labelText(label: ReactNode, fallback: string): string {
  return typeof label === "string" ? label : fallback;
}

/**
 * Legacy Frame/NavBar/DarkThemeSwitcher group: exclusive MUI ToggleButtonGroup
 * (color primary) with the three Sun/SunWithTime/Moon icon + text buttons.
 * Radius, borders, selected/hover colours and the textTransform:none override
 * all come from the legacy MUI theme.
 */
export function ThemeSwitchGroup({
  value,
  onChange,
  labels = DEFAULT_LABELS,
  showLabels = true,
  size = "md",
  disabled = false,
  className,
  sx,
}: ThemeSwitchGroupProps) {
  return (
    <ToggleButtonGroup
      className={className}
      sx={sx}
      color="primary"
      exclusive
      value={value}
      disabled={disabled}
      onChange={(_event, next: PudansColorModePreference | null) => {
        if (next) {
          onChange(next);
        }
      }}
      size={size === "sm" ? "small" : undefined}
      aria-label="Color theme"
    >
      {OPTIONS.map((option) => (
        <ToggleButton
          key={option.value}
          value={option.value}
          aria-label={labelText(labels[option.value], option.value)}
        >
          <Box
            component="span"
            sx={{ display: "inline-flex", mr: showLabels ? 1 : 0, color: "inherit" }}
          >
            <Icon name={option.icon} size={20} />
          </Box>
          {showLabels ? labels[option.value] : null}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
}

/**
 * Legacy Frame/NavBar/DarkThemeSwitcher: Tooltip > IconButton(DarkTheme, large)
 * > Popover anchored bottom-left holding the ToggleButtonGroup. The inline
 * variant mirrors the legacy PreferenceSetting usage (SwitchPopover without
 * onClose).
 */
export function ThemeSwitch({
  labels = DEFAULT_LABELS,
  showLabels = true,
  size = "md",
  disabled = false,
  className,
  "aria-label": ariaLabel = "Color theme",
  style,
  sx,
  variant = "popover",
}: ThemeSwitchProps) {
  const { preference, setPreference } = usePudansTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const group = (
    <ThemeSwitchGroup
      value={preference}
      onChange={setPreference}
      labels={labels}
      showLabels={showLabels}
      size={size}
      disabled={disabled}
      sx={variant === "inline" ? sx : undefined}
    />
  );

  if (variant === "inline") {
    return (
      <Box className={className} style={style} sx={{ display: "inline-flex" }}>
        {group}
      </Box>
    );
  }

  return (
    <>
      <Tooltip title={ariaLabel}>
        <span>
          <IconButton
            className={className}
            style={style}
            sx={sx}
            size={size === "sm" ? "small" : "large"}
            disabled={disabled}
            aria-label={ariaLabel}
            onClick={(event) => setAnchorEl(event.currentTarget)}
          >
            <Icon name="pd:DarkTheme" size={size === "sm" ? 20 : 24} />
          </IconButton>
        </span>
      </Tooltip>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
        transformOrigin={{ vertical: "top", horizontal: "left" }}
      >
        {group}
      </Popover>
    </>
  );
}
