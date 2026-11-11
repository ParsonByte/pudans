import { forwardRef, useId, type ReactNode } from "react";
import {
  FormControl,
  FormHelperText,
  FormLabel,
  InputAdornment,
  OutlinedInput,
  type OutlinedInputProps,
} from "@mui/material";
import { cn } from "../../utils/cn";
import styles from "./input.module.css";

export type InputSize = "sm" | "md" | "lg";

export interface InputProps extends Omit<
  OutlinedInputProps,
  "size" | "startAdornment" | "endAdornment"
> {
  size?: InputSize;
  label?: ReactNode;
  helperText?: ReactNode;
  startIcon?: ReactNode;
  endIcon?: ReactNode;
}

/**
 * Enterprise input built from FormControl + OutlinedInput. The label is a
 * separate block above the field (never notched into the border), which keeps
 * focus state clean in both light and dark themes.
 */
export const Input = forwardRef<HTMLDivElement, InputProps>(function Input(
  {
    size = "md",
    label,
    helperText,
    startIcon,
    endIcon,
    className,
    error = false,
    fullWidth = true,
    disabled = false,
    required = false,
    id,
    inputProps,
    ...props
  },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? `pudans-input-${generatedId.replace(/:/g, "")}`;
  const helperId = helperText ? `${inputId}-helper` : undefined;

  return (
    <FormControl
      ref={ref}
      fullWidth={fullWidth}
      error={error}
      disabled={disabled}
      required={required}
      className={cn(styles.root, styles[size], error && styles.error, className)}
    >
      {label ? (
        <FormLabel htmlFor={inputId} className={styles.label}>
          {label}
        </FormLabel>
      ) : null}

      <OutlinedInput
        id={inputId}
        notched={false}
        error={error}
        disabled={disabled}
        required={required}
        inputProps={{
          "aria-describedby": helperId,
          ...inputProps,
        }}
        startAdornment={
          startIcon ? (
            <InputAdornment position="start" className={styles.adornment}>
              {startIcon}
            </InputAdornment>
          ) : undefined
        }
        endAdornment={
          endIcon ? (
            <InputAdornment position="end" className={styles.adornment}>
              {endIcon}
            </InputAdornment>
          ) : undefined
        }
        {...props}
      />

      {helperText ? (
        <FormHelperText id={helperId} className={styles.helper}>
          {helperText}
        </FormHelperText>
      ) : null}
    </FormControl>
  );
});

Input.displayName = "Input";
