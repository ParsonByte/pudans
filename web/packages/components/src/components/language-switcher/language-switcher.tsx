import { useState, type CSSProperties } from "react";
import {
  IconButton,
  Menu,
  MenuItem,
  Tooltip,
  Typography,
  type SxProps,
  type Theme,
} from "@mui/material";
import { Icon } from "../icon";

export interface LanguageOption {
  code: string;
  label: string;
  description?: string;
}

export interface LanguageSwitcherProps {
  languages: readonly LanguageOption[];
  value: string;
  onChange: (code: string) => void;
  /** Icon-only trigger when true. */
  compact?: boolean;
  showCurrentLabel?: boolean;
  ariaLabel?: string;
  className?: string;
  style?: CSSProperties;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Common/LanguageSwitcher: Tooltip > IconButton(Translate) > MUI Menu with
 * one 14px MenuItem per language; the active language is highlighted through the
 * MUI `selected` state (action.selected background + primary text). Menu paper
 * radius (8px), elevation (3), list padding (4px 0) and MenuItem radius (8px)
 * come from the shared legacy theme overrides.
 */
export function LanguageSwitcher({
  languages,
  value,
  onChange,
  compact = false,
  showCurrentLabel = false,
  ariaLabel = "Change language",
  className,
  style,
  sx,
}: LanguageSwitcherProps) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const current = languages.find((language) => language.code === value);
  const showLabel = !compact && showCurrentLabel;
  const callerSx = Array.isArray(sx) ? sx : sx ? [sx] : [];

  return (
    <>
      <Tooltip title={ariaLabel}>
        <IconButton
          className={className}
          style={style}
          sx={[{ ml: 1 }, ...callerSx]}
          aria-label={ariaLabel}
          onClick={(event) => setAnchorEl(event.currentTarget)}
        >
          <Icon name="pd:Translate" size={24} />
          {showLabel ? (
            <Typography component="span" variant="body2" sx={{ ml: 1, lineHeight: 1 }}>
              {current?.label ?? value}
            </Typography>
          ) : null}
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        {languages.map((language) => (
          <MenuItem
            key={language.code}
            selected={language.code === value}
            sx={{ fontSize: 14 }}
            onClick={() => {
              onChange(language.code);
              setAnchorEl(null);
            }}
          >
            {language.label}
            {language.description && !showLabel ? (
              <Typography component="span" variant="caption" sx={{ ml: 1, color: "text.secondary" }}>
                {language.description}
              </Typography>
            ) : null}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
