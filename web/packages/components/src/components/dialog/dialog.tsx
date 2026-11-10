import { useId, type MouseEvent, type ReactNode } from "react";
import {
  Dialog as MuiDialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grow,
  IconButton,
  type DialogProps as MuiDialogProps,
} from "@mui/material";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./dialog.module.css";

export type DialogSize = "sm" | "md" | "lg";

export interface DialogProps extends Omit<MuiDialogProps, "title" | "slotProps" | "children"> {
  children: ReactNode;
  title?: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  footer?: ReactNode;
  size?: DialogSize;
  showCloseButton?: boolean;
  dividers?: boolean;
  closeLabel?: string;
}

/**
 * Modal shell with a 12px corner radius, blurred backdrop and optional header,
 * description and action footer. Backdrop and paper styles are local modules.
 */
export function Dialog({
  children,
  title,
  description,
  icon,
  footer,
  size = "md",
  showCloseButton = true,
  dividers = false,
  closeLabel = "Close dialog",
  onClose,
  className,
  ...props
}: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const hasHeader = Boolean(title || description || icon || showCloseButton);

  const handleClose = (event: MouseEvent<HTMLButtonElement>): void => {
    onClose?.(event, "escapeKeyDown");
  };

  return (
    <MuiDialog
      {...props}
      fullWidth
      maxWidth={false}
      scroll="paper"
      onClose={onClose}
      aria-labelledby={title ? titleId : undefined}
      aria-describedby={description ? descriptionId : undefined}
      className={className}
      slots={{ transition: Grow }}
      slotProps={{
        backdrop: { className: styles.backdrop },
        paper: { className: cn(styles.paper, styles[size]) },
      }}
    >
      {hasHeader ? (
        <DialogTitle component="div" className={styles.header}>
          <div className={styles.titleContent}>
            {icon ? (
              <span className={styles.icon} aria-hidden="true">
                {icon}
              </span>
            ) : null}
            <div className={styles.titleText}>
              {title ? (
                <h2 id={titleId} className={styles.heading}>
                  {title}
                </h2>
              ) : null}
              {description ? (
                <p id={descriptionId} className={styles.description}>
                  {description}
                </p>
              ) : null}
            </div>
          </div>
          {showCloseButton ? (
            <IconButton
              className={styles.close}
              size="small"
              aria-label={closeLabel}
              onClick={handleClose}
            >
              <Icon name="mdi:close-outline" size={20} />
            </IconButton>
          ) : null}
        </DialogTitle>
      ) : null}

      <DialogContent className={styles.content} dividers={dividers}>
        {children}
      </DialogContent>

      {footer ? <DialogActions className={styles.footer}>{footer}</DialogActions> : null}
    </MuiDialog>
  );
}

/** Semantic alias for consumers that prefer Modal naming. */
export const Modal = Dialog;
