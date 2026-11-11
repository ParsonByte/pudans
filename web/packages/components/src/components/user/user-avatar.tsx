import type { CSSProperties, ReactNode } from "react";
import { Avatar as MuiAvatar, Box, Skeleton, type SxProps, type Theme } from "@mui/material";
import type { AvatarStatus } from "../avatar";
import { Icon } from "../icon";

export type UserAvatarSize = "sm" | "md" | "lg" | "xl" | number;

const SIZE_MAP: Record<Exclude<UserAvatarSize, number>, number> = {
  sm: 24,
  md: 32,
  lg: 40,
  xl: 80,
};

const STATUS_COLOR: Record<AvatarStatus, string> = {
  online: "success.main",
  away: "warning.main",
  busy: "error.main",
  offline: "text.disabled",
};

/** Legacy UserAvatar stringToColor: deterministic full-saturation colour per nickname. */
export function userColor(name?: string): string {
  if (!name) {
    return "grey.500";
  }
  let hash = 0;
  for (let index = 0; index < name.length; index += 1) {
    hash = name.charCodeAt(index) + ((hash << 5) - hash);
  }
  let color = "#";
  for (let index = 0; index < 3; index += 1) {
    const value = (hash >> (index * 8)) & 0xff;
    color += `00${value.toString(16)}`.slice(-2);
  }
  return color;
}

export interface UserAvatarProps {
  name?: string;
  src?: string;
  alt?: string;
  /** "xl" is the 80px card avatar used by UserPopover. */
  size?: UserAvatarSize;
  square?: boolean;
  loading?: boolean;
  /** Anonymous user: shows the legacy InPrivate glyph instead of an image. */
  anonymous?: boolean;
  icon?: ReactNode;
  status?: AvatarStatus;
  statusLabel?: string;
  className?: string;
  style?: CSSProperties;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Common/User/UserAvatar: MUI Avatar with a hash colour derived from the
 * nickname (grey.500 when anonymous), same-size circular Skeleton while the user
 * is not loaded, InPrivate glyph for anonymous users and optional 12px rounding
 * when `square`. Sizing mirrors the legacy call sites (24/30/32/40/80px).
 */
export function UserAvatar({
  name,
  src,
  alt,
  size = "md",
  square = false,
  loading = false,
  anonymous = false,
  icon,
  status,
  statusLabel,
  className,
  style,
  sx,
}: UserAvatarProps) {
  const dimension = typeof size === "number" ? size : SIZE_MAP[size];
  const callerSx = Array.isArray(sx) ? sx : sx ? [sx] : [];

  if (loading) {
    return (
      <Box
        component="span"
        className={className}
        style={style}
        sx={{ display: "inline-flex", flexShrink: 0, width: dimension, height: dimension }}
      >
        <Skeleton variant="circular" width={dimension} height={dimension} sx={callerSx} />
      </Box>
    );
  }

  return (
    <Box
      component="span"
      className={className}
      style={style}
      sx={{ position: "relative", display: "inline-flex", flexShrink: 0, width: dimension, height: dimension }}
    >
      <MuiAvatar
        alt={alt ?? name ?? ""}
        src={anonymous ? undefined : src}
        sx={[
          {
            width: dimension,
            height: dimension,
            bgcolor: userColor(anonymous ? undefined : name),
            fontSize: Math.round(dimension * 0.6),
            borderRadius: square ? 1 : undefined,
          },
          ...callerSx,
        ]}
      >
        {anonymous ? (
          <Icon name="cr:InPrivate" size={Math.round(dimension * 0.6)} />
        ) : (
          icon
        )}
      </MuiAvatar>
      {status ? (
        <Box
          component="span"
          role="img"
          aria-label={statusLabel ?? status}
          sx={{
            position: "absolute",
            right: 0,
            bottom: 0,
            width: Math.max(8, Math.round(dimension * 0.28)),
            height: Math.max(8, Math.round(dimension * 0.28)),
            borderRadius: "50%",
            bgcolor: STATUS_COLOR[status],
            border: 2,
            borderColor: "background.paper",
          }}
        />
      ) : null}
    </Box>
  );
}
