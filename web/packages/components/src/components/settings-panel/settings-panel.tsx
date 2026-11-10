import { useRef, type CSSProperties, type ReactNode } from "react";
import { Slide } from "@mui/material";
import { cn } from "../../utils/cn";
import { Button } from "../button";
import { useDirtyState } from "./use-dirty-state";
import styles from "./settings-panel.module.css";

export interface FormItemProps {
  label?: ReactNode;
  description?: ReactNode;
  htmlFor?: string;
  required?: boolean;
  inline?: boolean;
  className?: string;
  children: ReactNode;
}

export function FormItem({
  label,
  description,
  htmlFor,
  required = false,
  inline = false,
  className,
  children,
}: FormItemProps) {
  return (
    <div className={cn(styles.item, inline && styles.itemInline, className)}>
      <div className={styles.itemText}>
        {label ? (
          <label className={styles.itemLabel} htmlFor={htmlFor}>
            {label}
            {required ? <span className={styles.required}>*</span> : null}
          </label>
        ) : null}
        {description ? <p className={styles.itemDescription}>{description}</p> : null}
      </div>
      <div className={styles.itemControl}>{children}</div>
    </div>
  );
}

export interface SettingsPanelProps<T extends object> {
  initialValues: T;
  values: T;
  onSave?: () => void | Promise<unknown>;
  onReset?: () => void;
  saving?: boolean;
  saveLabel?: ReactNode;
  savingLabel?: ReactNode;
  resetLabel?: ReactNode;
  dirtyLabel?: ReactNode;
  /** Controlled dirty state. Omit to compare initialValues with values. */
  dirty?: boolean;
  onDirtyChange?: (dirty: boolean) => void;
  /** Replaces the default reset/save buttons (e.g. publish or discard). */
  actions?: ReactNode;
  maxWidth?: number;
  className?: string;
  children: ReactNode;
}

/**
 * App-agnostic dirty-state form shell. Any editable surface (settings, profile,
 * wizard step, inline editor) can use it: the floating bar slides in only when
 * the current values differ from the loaded snapshot. The default bar exposes
 * reset/save actions; pass `actions` to replace them with domain-specific ones.
 */
export function SettingsPanel<T extends object>({
  initialValues,
  values,
  onSave,
  onReset,
  saving = false,
  saveLabel,
  savingLabel,
  resetLabel,
  dirtyLabel,
  dirty: dirtyProp,
  onDirtyChange,
  actions,
  maxWidth = 640,
  className,
  children,
}: SettingsPanelProps<T>) {
  const dirty = useDirtyState(initialValues, values, {
    dirty: dirtyProp,
    onDirtyChange,
  });
  const barRef = useRef<HTMLDivElement>(null);

  return (
    <div
      className={cn(styles.panel, className)}
      style={{ "--pd-form-panel-width": maxWidth + "px" } as CSSProperties}
    >
      <div className={styles.fields}>{children}</div>

      <Slide direction="up" in={dirty} mountOnEnter unmountOnExit timeout={260}>
        <div ref={barRef} className={styles.barWrap}>
          <div className={styles.bar}>
            {dirtyLabel ? (
              <span className={styles.dirty}>
                <span className={styles.dot} aria-hidden="true" />
                {dirtyLabel}
              </span>
            ) : (
              <span />
            )}
            {actions ?? (
              <div className={styles.actions}>
                {onReset && resetLabel ? (
                  <Button variant="ghost" onClick={onReset} disabled={saving}>
                    {resetLabel}
                  </Button>
                ) : null}
                {onSave && saveLabel ? (
                  <Button
                    variant="primary"
                    onClick={() => {
                      void onSave();
                    }}
                    loading={saving}
                    loadingText={savingLabel}
                  >
                    {saveLabel}
                  </Button>
                ) : null}
              </div>
            )}
          </div>
        </div>
      </Slide>
    </div>
  );
}
