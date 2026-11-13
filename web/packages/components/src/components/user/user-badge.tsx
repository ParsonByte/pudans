import type { HTMLAttributes, ReactNode } from "react";
import { ButtonBase, Typography } from "@mui/material";
import type { AvatarStatus } from "../avatar";
import { cn } from "@pudans/shared";
import { UserAvatar } from "./user-avatar";

export type UserBadgeSize = "sm" | "md" | "lg";

export interface UserBadgeProps extends Omit<HTMLAttributes<HTMLDivElement>, "children" | "onClick"> {
  name: string;
  description?: ReactNode;
  src?: string;
  icon?: ReactNode;
  status?: AvatarStatus;
  size?: UserBadgeSize;
  onClick?: () => void;
  trailing?: ReactNode;
  className?: string;
}

const AVATAR_SIZE: Record<UserBadgeSize, number> = { sm: 24, md: 32, lg: 40 };

/**
 * Legacy Common/User/UserBadge: a DefaultButton row (flex, align center,
 * maxWidth 150px) holding the avatar and the nickname (body2), with 8px between
 * them. Optional description/trailing props extend the legacy API without
 * changing the default MUI look.
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
  return (
    <ButtonBase
      component="div"
      focusRipple
      onClick={onClick}
      className={cn(className)}
      sx={{
        display: "flex",
        alignItems: "center",
        maxWidth: "150px",
        minHeight: 32,
        px: 2,
        py: 0.75,
        borderRadius: 1.5,
        textAlign: "left",
        justifyContent: "flex-start",
        color: "text.primary",
      }}
      {...(props as Record<string, unknown>)}
    >
      <UserAvatar name={name} src={src} icon={icon} status={status} size={AVATAR_SIZE[size]} />
      <Typography
        component="span"
        variant="body2"
        noWrap
        sx={{ ml: 1, display: "flex", flexDirection: "column", minWidth: 0 }}
      >
        <Typography component="span" variant="body2" noWrap sx={{ color: "text.primary" }}>
          {name}
        </Typography>
        {description ? (
          <Typography component="span" variant="caption" noWrap sx={{ color: "text.secondary" }}>
            {description}
          </Typography>
        ) : null}
      </Typography>
      {trailing ? (
        <Typography component="span" sx={{ ml: 1, display: "inline-flex", flexShrink: 0 }}>
          {trailing}
        </Typography>
      ) : null}
    </ButtonBase>
  );
}
