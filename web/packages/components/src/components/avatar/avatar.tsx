import { forwardRef, type ReactNode } from "react";
import { Avatar as MuiAvatar, type AvatarProps as MuiAvatarProps } from "@mui/material";
import { clsx } from "clsx";
import styles from "./avatar.module.css";

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
 * Avatar with image, initials, icon and explicit fallback layers. If the image
 * fails, MUI reveals the children fallback underneath automatically.
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
  const isNumericSize = typeof size === "number";
  const content =
    fallback ??
    (name ? (
      getInitials(name)
    ) : icon ? (
      <span className={styles.icon} aria-hidden="true">
        {icon}
      </span>
    ) : null);

  return (
    <span className={clsx(styles.wrapper, wrapperClassName)}>
      <MuiAvatar
        ref={ref}
        src={src}
        alt={alt ?? name ?? ""}
        className={clsx(styles.root, !isNumericSize && styles[size], className)}
        style={{
          ...(isNumericSize
            ? {
                width: size,
                height: size,
                fontSize: Math.max(10, Math.round(size * 0.36)),
              }
            : undefined),
          ...style,
        }}
        {...props}
      >
        {content}
      </MuiAvatar>
      {status ? (
        <span
          className={clsx(styles.status, styles[status])}
          aria-label={statusLabel ?? status}
          role="img"
        />
      ) : null}
    </span>
  );
});

Avatar.displayName = "Avatar";
