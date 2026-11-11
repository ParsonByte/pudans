import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Button, Stack } from "@mui/material";
import { Dialog, StyledDialogContentText } from "../dialog";

export interface ConfirmOptions {
  title?: ReactNode;
  description?: ReactNode;
  confirmLabel?: ReactNode;
  cancelLabel?: ReactNode;
  tone?: "default" | "danger";
}

export interface ConfirmDialogProps extends ConfirmOptions {
  open: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Legacy Confirmation dialog: DraggableDialog with "Are you sure?" title,
 * body2 break-all content and a Cancel (text) + OK (contained) action bar.
 */
export function ConfirmDialog({
  open,
  title = "Are you sure?",
  description,
  confirmLabel = "OK",
  cancelLabel = "Cancel",
  tone = "default",
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const close = (): void => onCancel();

  return (
    <Dialog
      open={open}
      onClose={onCancel}
      title={title}
      showCloseButton
      closeLabel={typeof cancelLabel === "string" ? cancelLabel : "Cancel"}
      loading={loading}
      showActions
      hideOk
      footer={
        <Stack direction="row" spacing={1}>
          <Button disabled={loading} onClick={close}>
            {cancelLabel}
          </Button>
          <Button
            variant="contained"
            color={tone === "danger" ? "error" : "primary"}
            disabled={loading}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </Stack>
      }
    >
      <Stack spacing={2}>
        {description ? <StyledDialogContentText>{description}</StyledDialogContentText> : null}
      </Stack>
    </Dialog>
  );
}


type ConfirmFn = (options?: ConfirmOptions) => Promise<boolean>;

interface ConfirmContextValue {
  confirm: ConfirmFn;
}

interface ConfirmRequest {
  id: number;
  options: ConfirmOptions;
  resolve: (confirmed: boolean) => void;
}

const ConfirmContext = createContext<ConfirmContextValue | null>(null);

export interface ConfirmProviderProps {
  children: ReactNode;
}

/**
 * Promise-based confirmation manager migrated from the legacy global
 * Confirmation dialog (which used a Redux promise pool). Mount once near the
 * app root, then call confirm(options) anywhere.
 */
export function ConfirmProvider({ children }: ConfirmProviderProps) {
  const [queue, setQueue] = useState<ConfirmRequest[]>([]);
  const idRef = useRef(0);
  const current = queue[0];

  const confirm = useCallback<ConfirmFn>((options) => {
    return new Promise<boolean>((resolve) => {
      idRef.current += 1;
      setQueue((previous) => [...previous, { id: idRef.current, options: options ?? {}, resolve }]);
    });
  }, []);

  const settle = useCallback((confirmed: boolean) => {
    setQueue((previous) => {
      const [first, ...rest] = previous;
      first?.resolve(confirmed);
      return rest;
    });
  }, []);

  const value = useMemo<ConfirmContextValue>(() => ({ confirm }), [confirm]);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      <ConfirmDialog
        open={Boolean(current)}
        title={current?.options.title}
        description={current?.options.description}
        confirmLabel={current?.options.confirmLabel}
        cancelLabel={current?.options.cancelLabel}
        tone={current?.options.tone}
        onConfirm={() => settle(true)}
        onCancel={() => settle(false)}
      />
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): ConfirmFn {
  const context = useContext(ConfirmContext);
  if (!context) {
    throw new Error("useConfirm must be used inside <ConfirmProvider>.");
  }
  return context.confirm;
}