import {
  useEffect,
  useId,
  useRef,
  useState,
  type MouseEvent,
  type PointerEvent,
  type ReactNode,
} from "react";
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
  /** Drag the dialog by its header (ignored on coarse pointers). */
  draggable?: boolean;
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
  draggable = false,
  onClose,
  className,
  ...props
}: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const dragOrigin = useRef({ pointerX: 0, pointerY: 0, x: 0, y: 0 });
  const hasHeader = Boolean(title || description || icon || showCloseButton);

  useEffect(() => {
    if (!props.open) {
      setOffset({ x: 0, y: 0 });
    }
  }, [props.open]);

  const handleDragStart = (event: PointerEvent<HTMLDivElement>): void => {
    if (!draggable || event.button !== 0) {
      return;
    }
    const target = event.target as HTMLElement;
    if (target.closest("button, a, input, textarea, select")) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    dragOrigin.current = {
      pointerX: event.clientX,
      pointerY: event.clientY,
      x: offset.x,
      y: offset.y,
    };
  };

  const handleDragMove = (event: PointerEvent<HTMLDivElement>): void => {
    if (!draggable || !event.currentTarget.hasPointerCapture(event.pointerId)) {
      return;
    }
    setOffset({
      x: dragOrigin.current.x + (event.clientX - dragOrigin.current.pointerX),
      y: dragOrigin.current.y + (event.clientY - dragOrigin.current.pointerY),
    });
  };

  const handleDragEnd = (event: PointerEvent<HTMLDivElement>): void => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

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
        <DialogTitle
          component="div"
          className={styles.header}
          onPointerDown={draggable ? handleDragStart : undefined}
          onPointerMove={draggable ? handleDragMove : undefined}
          onPointerUp={draggable ? handleDragEnd : undefined}
          onPointerCancel={draggable ? handleDragEnd : undefined}
        >
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
