import { useEffect, useMemo, useRef, type ReactNode } from "react";
import { Box, Grow, Typography, useTheme } from "@mui/material";
import { cn } from "@pudans/shared";
import { Button } from "../button";
import { Icon } from "../icon";

function isEqualValue(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) {
    return true;
  }
  if (typeof left !== "object" || typeof right !== "object" || left === null || right === null) {
    return false;
  }
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) {
      return false;
    }
    return left.every((item, index) => isEqualValue(item, right[index]));
  }
  const leftRecord = left as Record<string, unknown>;
  const rightRecord = right as Record<string, unknown>;
  const leftKeys = Object.keys(leftRecord);
  const rightKeys = Object.keys(rightRecord);
  if (leftKeys.length !== rightKeys.length) {
    return false;
  }
  return leftKeys.every(
    (key) =>
      Object.prototype.hasOwnProperty.call(rightRecord, key) &&
      isEqualValue(leftRecord[key], rightRecord[key]),
  );
}

interface UseDirtyStateOptions {
  dirty?: boolean;
  onDirtyChange?: (dirty: boolean) => void;
}

/** Component-private dirty-state comparison (kept inside the component file). */
function useDirtyState<T>(initialValues: T, values: T, options: UseDirtyStateOptions = {}): boolean {
  const compared = useMemo(() => !isEqualValue(initialValues, values), [initialValues, values]);
  const dirty = options.dirty ?? compared;
  const callbackRef = useRef(options.onDirtyChange);
  callbackRef.current = options.onDirtyChange;
  useEffect(() => {
    callbackRef.current?.(dirty);
  }, [dirty]);
  return dirty;
}

export interface FormItemProps {
  label?: ReactNode;
  description?: ReactNode;
  htmlFor?: string;
  required?: boolean;
  inline?: boolean;
  className?: string;
  children: ReactNode;
}

/**
 * Setting row from the legacy Pages/Setting SettingForm: body2/600 label with a
 * 4px gap to the control. Non-inline rows render the caption helper text below
 * the control; inline rows keep label/help left and the control right.
 */
export function FormItem({
  label,
  description,
  htmlFor,
  required = false,
  inline = false,
  className,
  children,
}: FormItemProps) {
  const theme = useTheme();
  const labelBlock = (
    <Box sx={{ minWidth: 0 }}>
      {label ? (
        <Typography
          component="label"
          htmlFor={htmlFor}
          variant="body2"
          sx={{
            display: "block",
            fontWeight: 600,
            color: theme.palette.text.primary,
            marginBottom: theme.spacing(0.5),
          }}
        >
          {label}
          {required ? (
            <Box component="span" sx={{ marginLeft: theme.spacing(0.25), color: "error.main" }}>
              *
            </Box>
          ) : null}
        </Typography>
      ) : null}
      {inline && description ? (
        <Typography
          component="p"
          variant="caption"
          sx={{ margin: 0, color: theme.palette.text.secondary, lineHeight: 1.66 }}
        >
          {description}
        </Typography>
      ) : null}
    </Box>
  );

  return (
    <Box
      className={cn(className)}
      sx={
        inline
          ? {
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: theme.spacing(2),
              minWidth: 0,
            }
          : { minWidth: 0 }
      }
    >
      {inline ? (
        <>
          {labelBlock}
          <Box sx={{ minWidth: 0 }}>{children}</Box>
        </>
      ) : (
        <Box sx={{ minWidth: 0 }}>
          {labelBlock}
          <Box sx={{ minWidth: 0 }}>{children}</Box>
          {description ? (
            <Typography
              component="p"
              variant="caption"
              sx={{ margin: 0, marginTop: theme.spacing(1), color: theme.palette.text.secondary, lineHeight: 1.66 }}
            >
              {description}
            </Typography>
          ) : null}
        </Box>
      )}
    </Box>
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
  /** Optional content max width in px; the legacy settings page was fluid. */
  maxWidth?: number;
  className?: string;
  children: ReactNode;
}

/**
 * Settings shell rebuilt from the legacy Admin/Settings SettingWrapper:
 * fluid form column (24px section rhythm) plus the fixed SavingFloat bar —
 * paper background, 1px divider border, shape radius, 16px padding, bottom
 * 23px, zIndex.modal, Save + SecondaryButton inside a MUI Grow appearing only
 * when values are dirty. The controls are the only API-level addition.
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
  maxWidth,
  className,
  children,
}: SettingsPanelProps<T>) {
  const theme = useTheme();
  const dirty = useDirtyState(initialValues, values, {
    dirty: dirtyProp,
    onDirtyChange,
  });

  return (
    <Box
      className={cn(className)}
      sx={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        minWidth: 0,
        maxWidth: maxWidth ? theme.spacing(maxWidth / 8) : "none",
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          gap: theme.spacing(3),
          minWidth: 0,
        }}
      >
        {children}
      </Box>
      <Box sx={{ height: theme.spacing(8.75) }} />
      <Grow in={dirty}>
        <Box
          sx={{
            position: "fixed",
            bottom: theme.spacing(2.875),
            zIndex: theme.zIndex.modal,
            display: "flex",
            alignItems: "center",
            gap: theme.spacing(1),
            padding: theme.spacing(2),
            border: "var(--pd-border-width-thin) solid " + theme.palette.divider,
            borderRadius: `${theme.shape.borderRadius}px`,
            backgroundColor: theme.palette.background.paper,
          }}
        >
          {dirtyLabel ? (
            <Typography
              variant="caption"
              sx={{ color: theme.palette.text.secondary, marginRight: theme.spacing(1) }}
            >
              {dirtyLabel}
            </Typography>
          ) : null}
          {actions ?? (
            <>
              {onSave && saveLabel ? (
                <Button
                  variant="primary"
                  startIcon={<Icon name="pd:Save" size={20} />}
                  loading={saving}
                  loadingText={savingLabel}
                  onClick={() => {
                    void onSave();
                  }}
                >
                  {saveLabel}
                </Button>
              ) : null}
              {onReset && resetLabel ? (
                <Button
                  variant="secondary"
                  startIcon={<Icon name="pd:ArrowHookUpRight" size={20} />}
                  disabled={saving}
                  onClick={onReset}
                >
                  {resetLabel}
                </Button>
              ) : null}
            </>
          )}
        </Box>
      </Grow>
    </Box>
  );
}
