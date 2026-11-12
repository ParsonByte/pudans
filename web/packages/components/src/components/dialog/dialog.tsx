import {
  forwardRef,
  useId,
  useRef,
  useState,
  type MouseEvent,
  type PointerEvent,
  type ReactNode,
} from "react";
import {
  Box,
  Button,
  CircularProgress,
  Dialog as MuiDialog,
  DialogActions,
  DialogContent,
  DialogContentText as MuiDialogContentText,
  DialogTitle,
  Grow,
  IconButton,
  Paper,
  Stack,
  Typography,
  useMediaQuery,
  type DialogProps as MuiDialogProps,
  type PaperProps,
} from "@mui/material";
import { styled } from "@mui/material/styles";
import { Icon } from "../icon";

export type DialogSize = "sm" | "md" | "lg";

/** Legacy DraggableDialog: Paper is wrapped by react-draggable. */
const DraggablePaper = forwardRef<HTMLDivElement, PaperProps>(function DraggablePaper(props, ref) {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const drag = useRef({ pointerId: -1, startX: 0, startY: 0, originX: 0, originY: 0 });

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>): void => {
    if (event.button !== 0) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target.closest("[data-dialog-drag-handle]")) {
      return;
    }
    if (target.closest("button, a, input, textarea, select, [role='button']")) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: position.x,
      originY: position.y,
    };
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>): void => {
    if (drag.current.pointerId !== event.pointerId) {
      return;
    }
    setPosition({
      x: drag.current.originX + (event.clientX - drag.current.startX),
      y: drag.current.originY + (event.clientY - drag.current.startY),
    });
  };

  const endDrag = (event: PointerEvent<HTMLDivElement>): void => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    drag.current.pointerId = -1;
  };

  return (
    <Paper
      ref={ref}
      {...props}
      style={{
        ...props.style,
        translate: `${position.x}px ${position.y}px`,
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
    />
  );
});

/** Legacy StyledDialogActions: 16px 24px (dense 4px 24px) with space-between. */
const StyledDialogActions = styled(DialogActions, {
  shouldForwardProp: (prop) => prop !== "denseAction",
})<{ denseAction?: boolean }>(({ theme, denseAction }) => ({
  padding: `${theme.spacing(denseAction ? 0.5 : 2)} ${theme.spacing(3)}`,
  justifyContent: "space-between",
}));

/** Legacy StyledDialogContentText: body2 with break-all. */
export const StyledDialogContentText = styled(MuiDialogContentText)(({ theme }) => ({
  fontSize: theme.typography.body2.fontSize,
  wordBreak: "break-all",
})) as unknown as typeof MuiDialogContentText;

export interface DialogProps extends Omit<MuiDialogProps, "title" | "slotProps" | "children"> {
  children?: ReactNode;
  title?: ReactNode;
  /** Extra line rendered at the top of DialogContent (library extension). */
  description?: ReactNode;
  icon?: ReactNode;
  /** Custom footer content; legacy action props below can be used instead. */
  footer?: ReactNode;
  /** Maps to the MUI Dialog maxWidth breakpoints. Defaults to "sm" (legacy 600px). */
  size?: DialogSize;
  showCloseButton?: boolean;
  dividers?: boolean;
  closeLabel?: string;
  /** Drag the dialog by its title bar (ignored on coarse pointers). */
  draggable?: boolean;
  /** Legacy DraggableDialog loading state: disables close / action buttons. */
  loading?: boolean;
  /** Legacy DraggableDialog disabled state for the OK button. */
  disabled?: boolean;
  /** Legacy DraggableDialog action bar. */
  showActions?: boolean;
  showCancel?: boolean;
  hideOk?: boolean;
  okText?: ReactNode;
  cancelText?: ReactNode;
  onAccept?: () => void;
  denseActions?: boolean;
  secondaryAction?: ReactNode;
  secondaryFullWidth?: boolean;
}

function LoadingButtonLabel({ loading, children }: { loading?: boolean; children: ReactNode }) {
  return (
    <Box component="span" sx={{ position: "relative", display: "inline-flex", alignItems: "center" }}>
      <Box component="span" sx={{ visibility: loading ? "hidden" : "visible" }}>
        {children}
      </Box>
      {loading ? (
        <CircularProgress
          size={16}
          color="inherit"
          sx={{ position: "absolute", top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
        />
      ) : null}
    </Box>
  );
}

/**
 * Legacy DraggableDialog rebuilt on MUI Dialog: Grow transition, 12px radius,
 * elevation 24 paper, title + Dismiss IconButton, DialogContent with paddingTop
 * 0 (theme override) and the legacy action bar (Cancel text + OK contained).
 */
export function Dialog({
  children,
  title,
  description,
  icon,
  footer,
  size = "sm",
  showCloseButton = true,
  dividers = false,
  closeLabel = "Close dialog",
  draggable = false,
  loading = false,
  disabled = false,
  showActions = false,
  showCancel = true,
  hideOk = false,
  okText = "OK",
  cancelText = "Cancel",
  onAccept,
  denseActions = false,
  secondaryAction,
  secondaryFullWidth = false,
  onClose,
  className,
  fullWidth = false,
  maxWidth,
  ...props
}: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const isTouch = useMediaQuery("(pointer: coarse)");
  const dragEnabled = draggable && !isTouch;
  const hasHeader = Boolean(title || icon || showCloseButton);
  const hasActions = Boolean(footer || showActions || onAccept);

  const handleClose = (event: MouseEvent<HTMLButtonElement>): void => {
    onClose?.(event, "backdropClick");
  };

  const legacyMaxWidths: Record<DialogSize, "sm" | "md" | "lg"> = {
    sm: "sm",
    md: "md",
    lg: "lg",
  };

  return (
    <MuiDialog
      {...props}
      fullWidth={fullWidth}
      maxWidth={maxWidth ?? legacyMaxWidths[size]}
      scroll={props.scroll ?? "paper"}
      onClose={loading ? undefined : onClose}
      aria-labelledby={title ? titleId : undefined}
      aria-describedby={description ? descriptionId : undefined}
      className={className}
      slots={{ paper: DraggablePaper, transition: Grow }}
    >
      {hasHeader ? (
        <DialogTitle
          id={title ? titleId : undefined}
          data-dialog-drag-handle={dragEnabled ? "true" : undefined}
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            cursor: dragEnabled ? "move" : "initial",
            userSelect: dragEnabled ? "none" : undefined,
            touchAction: dragEnabled ? "none" : undefined,
          }}
        >
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
            {icon ? (
              <Box component="span" aria-hidden="true" sx={{ display: "inline-flex", alignItems: "center" }}>
                {icon}
              </Box>
            ) : null}
            <Box sx={{ minWidth: 0 }}>{title}</Box>
          </Box>
          {showCloseButton ? (
            <IconButton disabled={loading} onClick={handleClose} aria-label={closeLabel}>
              <Icon name="pd:Dismiss" size={20} />
            </IconButton>
          ) : null}
        </DialogTitle>
      ) : null}

      <DialogContent dividers={dividers}>
        {description ? (
          <Typography
            id={descriptionId}
            variant="body2"
            color="text.secondary"
            sx={{ mb: children ? 2 : 0 }}
          >
            {description}
          </Typography>
        ) : null}
        {children}
      </DialogContent>

      {hasActions ? (
        <StyledDialogActions denseAction={denseActions}>
          {secondaryAction ? (
            <Box sx={{ flexGrow: secondaryFullWidth ? 1 : "unset" }}>{secondaryAction}</Box>
          ) : null}
          <Stack direction="row" spacing={1}>
            {footer}
            {showCancel && !footer ? (
              <Button disabled={loading} onClick={handleClose}>
                {cancelText}
              </Button>
            ) : null}
            {!hideOk && onAccept ? (
              <Button disabled={disabled || loading} variant="contained" color="primary" onClick={onAccept}>
                <LoadingButtonLabel loading={loading}>{okText}</LoadingButtonLabel>
              </Button>
            ) : null}
          </Stack>
        </StyledDialogActions>
      ) : null}
    </MuiDialog>
  );
}

/** Semantic alias for consumers that prefer Modal naming. */
export const Modal = Dialog;