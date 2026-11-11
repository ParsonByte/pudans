import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Avatar, type AvatarStatus } from "../avatar";
import styles from "./user.module.css";

export type UserBadgeSize = "sm" | "md" | "lg";

export interface UserBadgeProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  name: string;
  description?: ReactNode;
  src?: string;
  icon?: ReactNode;
  status?: AvatarStatus;
  size?: UserBadgeSize;
  onClick?: () => void;
  trailing?: ReactNode;
}

/**
 * Person summary row migrated from the legacy Common/User/UserBadge. Composes
 * Avatar + name + secondary line and works as a static row or a button.
 */
export function UserBadge({
  name,
  description,
  src,
  icon,
  status,
  size = "md",
  onClick,
  trailing,
  className,
  ...props
}: UserBadgeProps) {
  const content = (
    <>
      <Avatar
        src={src}
        name={name}
        icon={icon}
        status={status}
        size={size === "sm" ? 24 : size === "lg" ? 40 : 32}
      />
      <span className={styles.badgeBody}>
        <span className={styles.badgeName}>{name}</span>
        {description ? <span className={styles.badgeDescription}>{description}</span> : null}
      </span>
      {trailing ? <span className={styles.trailing}>{trailing}</span> : null}
    </>
  );

  const classes = cn(
    styles.badge,
    styles["badge-" + size],
    onClick && styles.interactive,
    className,
  );

  if (onClick) {
    return (
      <button
        type="button"
        className={classes}
        onClick={onClick}
        {...(props as Record<string, unknown>)}
      >
        {content}
      </button>
    );
  }

  return (
    <div className={classes} {...props}>
      {content}
    </div>
  );
}
