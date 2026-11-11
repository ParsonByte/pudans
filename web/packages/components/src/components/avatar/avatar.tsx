import { forwardRef, type ReactNode } from "react";
import { Avatar as MuiAvatar, type AvatarProps as MuiAvatarProps, useTheme } from "@mui/material";
import { cn } from "@pudans/utils";

export type AvatarSize = "sm" | "md" | "lg" | "xl" | number;
export type AvatarStatus = "online" | "offline" | "away" | "busy";

export interface AvatarProps extends Omit<MuiAvatarProps, "src" | "alt" | "children"> {
  src?: string;
  alt?: string;
  name?: string;
  icon?: ReactNode;
  fallback?: ReactNode;
  size?: AvatarSize;
  status?: AvatarStatus;
  statusLabel?: string;
  wrapperClassName?: string;
}

const SIZE_MAP: Record<"sm" | "md" | "lg" | "xl", number> = {
  sm: 24,
  md: 32,
  lg: 40,
  xl: 48,
};

const FALLBACK_COLORS = ["primary", "secondary", "success", "warning", "info", "error"] as const;

/** Deterministic legacy-equivalent colour identity for text avatars. */
function hashString(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = value.charCodeAt(index) + ((hash << 5) - hash);
  }
  return Math.abs(hash);
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "?";
  }
  if (parts.length === 1) {
    return Array.from(parts[0] ?? "")
      .slice(0, 2)
      .join("")
      .toUpperCase();
  }
  const first = Array.from(parts[0] ?? "")[0] ?? "";
  const last = Array.from(parts[parts.length - 1] ?? "")[0] ?? "";
  return `${first}${last}`.toUpperCase();
}

/**
 * MUI Avatar rebuilt with the legacy UserAvatar colours/typography: no border,
 * deterministic per-name background (old stringToColor) mapped onto theme
 * palette colours, grey[500] for anonymous users, MUI Avatars own font sizing
 * and fallback chain for broken images.
 */
export const Avatar = forwardRef<HTMLDivElement, AvatarProps>(function Avatar(
  {
    src,
    alt,
    name,
    icon,
    fallback,
    size = "md",
    status,
    statusLabel,
    className,
    wrapperClassName,
    style,
    ...props
  },
  ref,
) {
  const theme = useTheme();
  const isNumericSize = typeof size === "number";
  const pixels = isNumericSize ? size : SIZE_MAP[size];
  const paletteKey = FALLBACK_COLORS[hashString(name ?? "") % FALLBACK_COLORS.length];
  const palette = theme.palette[paletteKey];
  const background = name ? palette.main : theme.palette.grey[500];
  const color = name ? palette.contrastText : theme.palette.getContrastText(theme.palette.grey[500]);
  const content =
    fallback ??
    (name ? (
      getInitials(name)
    ) : icon ? (
      <span style={{ display: "inline-flex", width: "55%", height: "55%" }} aria-hidden="true">
        {icon}
      </span>
    ) : null);

  return (
    <span style={{ position: "relative", display: "inline-flex", flex: "0 0 auto" }} className={cn(wrapperClassName)}>
      <MuiAvatar
        {...props}
        ref={ref}
        src={src}
        alt={alt ?? name ?? ""}
        className={cn(className)}
        sx={[
          {
            width: pixels,
            height: pixels,
            fontSize: pixels * 0.5,
            bgcolor: background,
            color,
          },
          ...(Array.isArray(props.sx) ? props.sx : [props.sx]),
        ]}
      >
        {content}
      </MuiAvatar>
      {status ? (
        <span
          aria-label={statusLabel ?? status}
          role="img"
          style={{
            position: "absolute",
            right: 0,
            bottom: 0,
            width: 10,
            height: 10,
            borderRadius: "50%",
            border: `2px solid ${theme.palette.background.paper}`,
            backgroundColor:
              status === "online"
                ? theme.palette.success.main
                : status === "away"
                  ? theme.palette.warning.main
                  : status === "busy"
                    ? theme.palette.error.main
                    : theme.palette.text.disabled,
          }}
        />
      ) : null}
    </span>
  );
});

Avatar.displayName = "Avatar";