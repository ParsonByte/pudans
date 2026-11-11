import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import { usePudansTheme } from "../../styles/theme-provider";
import type { PudansColorModePreference } from "../../styles/tokens";
import { Icon } from "../icon";
import styles from "./theme-switch.module.css";

export interface ThemeSwitchLabels {
  light: ReactNode;
  system: ReactNode;
  dark: ReactNode;
}

export interface ThemeSwitchProps {
  labels?: ThemeSwitchLabels;
  showLabels?: boolean;
  size?: "sm" | "md";
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
}

const OPTIONS: Array<{ value: PudansColorModePreference; icon: string }> = [
  { value: "light", icon: "cr:Sunny" },
  { value: "system", icon: "cr:SunWithTime" },
  { value: "dark", icon: "cr:Moon" },
];

/**
 * Theme preference segmented control migrated from the legacy
 * DarkThemeSwitcher. Consumes the pudans theme context directly.
 */
export function ThemeSwitch({
  labels = { light: "Light", system: "System", dark: "Dark" },
  showLabels = false,
  size = "md",
  disabled = false,
  className,
  "aria-label": ariaLabel = "Color theme",
}: ThemeSwitchProps) {
  const { preference, setPreference } = usePudansTheme();

  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className={cn(styles.root, styles[size], disabled && styles.disabled, className)}
    >
      {OPTIONS.map((option) => {
        const selected = preference === option.value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={
              typeof labels[option.value] === "string"
                ? (labels[option.value] as string)
                : option.value
            }
            className={cn(styles.option, selected && styles.selected)}
            disabled={disabled}
            onClick={() => setPreference(option.value)}
          >
            <Icon name={option.icon} size={size === "sm" ? 15 : 17} />
            {showLabels ? <span className={styles.label}>{labels[option.value]}</span> : null}
          </button>
        );
      })}
    </div>
  );
}
