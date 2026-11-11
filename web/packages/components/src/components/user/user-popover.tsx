import type { ReactNode } from "react";
import { Box, Popover, Typography } from "@mui/material";
import type { AvatarStatus } from "../avatar";
import { UserAvatar } from "./user-avatar";

export interface UserPopoverUser {
  name: string;
  description?: ReactNode;
  src?: string;
  status?: AvatarStatus;
  anonymous?: boolean;
}

export interface UserPopoverProps {
  open: boolean;
  anchorEl: HTMLElement | null;
  user: UserPopoverUser;
  onClose: () => void;
  actions?: ReactNode;
  footer?: ReactNode;
  className?: string;
}

/**
 * Legacy Common/User/UserPopover: Paper popover anchored bottom-left with a
 * 300px min-width and 16px padding; inside, an 80x80 avatar + 16px gap and the
 * right column with the h6 nickname (600), group/secondary line and the action
 * icon-button row. Paper radius/elevation are the MUI defaults (12px / 8).
 */
export function UserPopover({
  open,
  anchorEl,
  user,
  onClose,
  actions,
  footer,
}: UserPopoverProps) {
  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
      transformOrigin={{ vertical: "top", horizontal: "left" }}
      slotProps={{
        paper: {
          sx: {
            minWidth: (theme) => theme.spacing(37.5),
            p: 2,
          },
        },
      }}
    >
      <Box sx={{ display: "flex" }}>
        <UserAvatar
          name={user.name}
          src={user.src}
          status={user.status}
          anonymous={user.anonymous}
          size={80}
        />
        <Box sx={{ ml: 2, minWidth: 0 }}>
          <Typography variant="h6" noWrap sx={{ fontWeight: 600 }}>
            {user.name}
          </Typography>
          {user.description ? (
            <Typography variant="body2" color="text.secondary">
              {user.description}
            </Typography>
          ) : null}
          {actions ? (
            <Box sx={{ mt: 0.5, display: "flex", alignItems: "center", position: "relative", left: -10 }}>
              {actions}
            </Box>
          ) : null}
        </Box>
      </Box>
      {footer ? <Box sx={{ mt: 2 }}>{footer}</Box> : null}
    </Popover>
  );
}
