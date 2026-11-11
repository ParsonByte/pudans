import { Icon as NetworkIcon } from "@iconify/react";
import { Icon as OfflineIcon, type IconProps as IconifyProps } from "@iconify/react/offline";
import { Box, useTheme } from "@mui/material";
import type { SxProps, Theme } from "@mui/material/styles";
import { cn } from "@pudans/utils";
import { isRegisteredIcon, registerBuiltinIcons, registerCloudreveIcons } from "@pudans/icons";
import styles from "./icon.module.css";

registerBuiltinIcons();
registerCloudreveIcons();

export interface IconProps extends Omit<IconifyProps, "icon" | "color"> {
  /** Iconify identifier, for example "mdi:home-outline". */
  name: string;
  /** Pixel (or CSS length) size; maps onto the MUI SvgIcon font-size box. */
  size?: number | string;
  /** MUI SvgIcon fontSize alias: small 20 / medium 24 / large 35 / inherit. */
  fontSize?: "inherit" | "small" | "medium" | "large";
  /** MUI SvgIcon colour semantics: palette keys or any CSS colour. Default inherit. */
  color?: string;
  htmlColor?: string;
  titleAccess?: string;
  label?: string;
  sx?: SxProps<Theme>;
  spin?: boolean;
}

const DEFAULT_SIZE = 3;

/**
 * Icon primitive rebuilt to match the legacy MUI SvgIcon 24x24 / currentColor
 * behaviour: 1em box, default medium = 24px, palette colour keys, inherit by
 * default and no outer margins. Registered icons render offline from the "cr"
 * and "mdi" collections; unknown names fall back to the Iconify API.
 */
export function Icon({
  name,
  size,
  fontSize = "medium",
  color = "inherit",
  htmlColor,
  titleAccess,
  label,
  sx,
  spin = false,
  className,
  style,
  ...props
}: IconProps) {
  const theme = useTheme();
  const resolvedSize =
    size ??
    (fontSize === "small"
      ? theme.spacing(2.5)
      : fontSize === "large"
        ? theme.spacing(4.375)
        : fontSize === "inherit"
          ? "inherit"
          : theme.spacing(DEFAULT_SIZE));
  const paletteColor = (() => {
    switch (color) {
      case "primary":
        return theme.palette.primary.main;
      case "secondary":
        return theme.palette.secondary.main;
      case "error":
        return theme.palette.error.main;
      case "warning":
        return theme.palette.warning.main;
      case "info":
        return theme.palette.info.main;
      case "success":
        return theme.palette.success.main;
      case "action":
        return theme.palette.action.active;
      case "disabled":
        return theme.palette.text.disabled;
      case "inherit":
        return "currentColor";
      default:
        return color;
    }
  })();
  const accessibleLabel = label ?? titleAccess;
  const iconProps = {
    icon: name,
    width: "100%",
    height: "100%",
    color: "currentColor",
    inline: true,
    "aria-hidden": true,
    ...props,
  };

  return (
    <Box
      component="span"
      className={cn(styles.icon, spin && styles.spin, className)}
      style={style}
      role={accessibleLabel ? "img" : undefined}
      aria-label={accessibleLabel}
      aria-hidden={accessibleLabel ? undefined : true}
      title={titleAccess}
      sx={{
        display: "inline-flex",
        flex: "0 0 auto",
        alignItems: "center",
        justifyContent: "center",
        width: "1em",
        height: "1em",
        fontSize: resolvedSize,
        lineHeight: 1,
        verticalAlign: "middle",
        color: htmlColor ?? paletteColor,
        fill: "currentColor",
        userSelect: "none",
        ...sx,
      }}
    >
      {isRegisteredIcon(name) ? <OfflineIcon {...iconProps} /> : <NetworkIcon {...iconProps} />}
    </Box>
  );
}
