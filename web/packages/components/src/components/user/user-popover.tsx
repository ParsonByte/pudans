import type { ReactNode } from "react";
import { Popover } from "@mui/material";
import { cn } from "../../utils/cn";
import { Avatar, type AvatarStatus } from "../avatar";
import styles from "./user.module.css";

export interface UserPopoverUser {
  name: string;
  description?: ReactNode;
  src?: string;
  status?: AvatarStatus;
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
 * User summary popover migrated from the legacy Common/User/UserPopover (which
 * was Redux-bound). Now fully controlled and content is injected by the app.
 */
export function UserPopover({
  open,
  anchorEl,
  user,
  onClose,
  actions,
  footer,
  className,
}: UserPopoverProps) {
  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
      transformOrigin={{ vertical: "top", horizontal: "right" }}
      slotProps={{ paper: { className: cn(styles.popover, className) } }}
    >
      <div className={styles.popoverHeader}>
        <Avatar src={user.src} name={user.name} status={user.status} size={40} />
        <div className={styles.popoverIdentity}>
          <span className={styles.badgeName}>{user.name}</span>
          {user.description ? (
            <span className={styles.badgeDescription}>{user.description}</span>
          ) : null}
        </div>
      </div>
      {actions ? <div className={styles.popoverBody}>{actions}</div> : null}
      {footer ? <div className={styles.popoverFooter}>{footer}</div> : null}
    </Popover>
  );
}
