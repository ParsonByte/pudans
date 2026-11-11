import { forwardRef, useId, type ReactNode } from "react";
import {
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select as MuiSelect,
  type SelectProps as MuiSelectProps,
} from "@mui/material";
import type { Theme } from "@mui/material/styles";
import type { InputSize } from "../input";
import { cn } from "@pudans/utils";

export interface SelectOption {
  label: ReactNode;
  value: string | number;
  disabled?: boolean;
}

export type SelectProps = Omit<MuiSelectProps, "size" | "label" | "labelId" | "children"> & {
  size?: InputSize;
  label?: ReactNode;
  helperText?: ReactNode;
  fullWidth?: boolean;
  options?: readonly SelectOption[];
  children?: ReactNode;
  displayEmpty?: boolean;
};

/** Legacy DenseSelect: 39px min height, 6px vertical paddings. */
function denseSx(theme: Theme) {
  return {
    minHeight: theme.spacing(4.875),
    "& .MuiOutlinedInput-input": {
      display: "flex",
      alignItems: "center",
      paddingTop: theme.spacing(0.75),
      paddingBottom: theme.spacing(0.75),
    },
  };
}

/**
 * Cloudreve outlined select: floating InputLabel + notched 12px outline and the
 * legacy dense (39px) variant for admin forms.
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
  const labelId = label ? `${selectId}-label` : undefined;
  const helperId = helperText ? `${selectId}-helper` : undefined;
  const dense = size === "sm";

  return (
    <FormControl
      ref={ref}
      fullWidth={fullWidth}
      error={error}
      disabled={disabled}
      required={required}
      variant="outlined"
      className={cn(className)}
      sx={dense ? denseSx : undefined}
    >
      {label ? (
        <InputLabel id={labelId} htmlFor={selectId}>
          {label}
        </InputLabel>
      ) : null}
      <MuiSelect
        id={selectId}
        labelId={labelId}
        label={label}
        size={dense ? "small" : "medium"}
        variant="outlined"
        displayEmpty={displayEmpty}
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
      {helperText ? <FormHelperText id={helperId}>{helperText}</FormHelperText> : null}
    </FormControl>
  );
});

Select.displayName = "Select";
