import { forwardRef, useId, type ReactNode } from "react";
import {
  FormControl,
  FormHelperText,
  FormLabel,
  MenuItem,
  Select as MuiSelect,
  type SelectProps as MuiSelectProps,
} from "@mui/material";
import type { InputSize } from "../input";
import { cn } from "../../utils/cn";
import styles from "./select.module.css";

export interface SelectOption {
  label: ReactNode;
  value: string | number;
  disabled?: boolean;
}

export interface SelectProps
  extends Omit<MuiSelectProps, "size" | "label" | "labelId" | "children"> {
  size?: InputSize;
  label?: ReactNode;
  helperText?: ReactNode;
  fullWidth?: boolean;
  options?: readonly SelectOption[];
  children?: ReactNode;
  displayEmpty?: boolean;
}

/**
 * Cloudreve-style outlined select with a fixed label above the field.
 */
export const Select = forwardRef<HTMLDivElement, SelectProps>(function Select(
  {
    size = "md",
    label,
    helperText,
    fullWidth = true,
    options,
    children,
    className,
    error = false,
    disabled = false,
    required = false,
    displayEmpty = true,
    id,
    ...props
  },
  ref,
) {
  const generatedId = useId();
  const selectId = id ?? `pudans-select-${generatedId.replace(/:/g, "")}`;
  const helperId = helperText ? `${selectId}-helper` : undefined;

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
        <FormLabel htmlFor={selectId} className={styles.label}>
          {label}
        </FormLabel>
      ) : null}

      <MuiSelect
        id={selectId}
        variant="outlined"
        displayEmpty={displayEmpty}
        error={error}
        inputProps={{ "aria-describedby": helperId }}
        {...props}
      >
        {options?.map((option) => (
          <MenuItem key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </MenuItem>
        ))}
        {children}
      </MuiSelect>

      {helperText ? (
        <FormHelperText id={helperId} className={styles.helper}>
          {helperText}
        </FormHelperText>
      ) : null}
    </FormControl>
  );
});

Select.displayName = "Select";
