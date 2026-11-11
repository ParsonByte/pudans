import type { ReactNode } from "react";
import { Dialog } from "../dialog";
import { CodeBlock } from "../code";
import styles from "./error-detail-dialog.module.css";

export interface ErrorDetail {
  key: string;
  label?: ReactNode;
  message: string;
}

export interface ErrorDetailDialogProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  message?: ReactNode;
  errors?: readonly ErrorDetail[];
  requestId?: string;
  footer?: ReactNode;
}

/**
 * Aggregated error inspector migrated from the legacy AggregatedErrorDetail
 * dialog. Renders a human message, per-item errors and the request id so users
 * can report issues.
 */
export function ErrorDetailDialog({
  open,
  onClose,
  title = "Error details",
  message,
  errors,
  requestId,
  footer,
}: ErrorDetailDialogProps) {
  return (
    <Dialog open={open} onClose={onClose} title={title} size="md" footer={footer}>
      {message ? <p className={styles.message}>{message}</p> : null}
      {errors && errors.length > 0 ? (
        <ul className={styles.list}>
          {errors.map((error) => (
            <li key={error.key} className={styles.item}>
              <span className={styles.itemLabel}>{error.label ?? error.key}</span>
              <span className={styles.itemMessage}>{error.message}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {requestId ? (
        <div className={styles.requestId}>
          <span className={styles.requestLabel}>Request ID</span>
          <CodeBlock code={requestId} showCopy={false} wrap />
        </div>
      ) : null}
    </Dialog>
  );
}
