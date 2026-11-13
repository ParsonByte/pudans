import { forwardRef, type ReactNode } from "react";
import { InputAdornment, TextField, type TextFieldProps } from "@mui/material";
import { cn } from "@pudans/shared";

export type InputSize = "sm" | "md" | "lg";

export type InputProps = Omit<TextFieldProps, "size" | "label" | "helperText" | "variant" | "select"> & {
  size?: InputSize;
  label?: ReactNode;
  helperText?: ReactNode;
  startIcon?: ReactNode;
  endIcon?: ReactNode;
  /** Legacy FilledTextField / OutlinedInput variants. Defaults to outlined. */
  variant?: "outlined" | "filled";
  /** Legacy lowercase html input attributes. */
  inputProps?: Record<string, unknown>;
};

/** DenseFilledTextField: 14px font, 9.6px vertical paddings, label at 10px. */
function denseSx(theme: import("@mui/material/styles").Theme) {
  return {
    "& .MuiOutlinedInput-root": {
      paddingTop: 0,
      paddingBottom: 0,
    },
    "& .MuiOutlinedInput-input": {
      paddingTop: theme.spacing(1.2),
      paddingBottom: theme.spacing(1.2),
      fontSize: theme.typography.body2.fontSize,
    },
    "& .MuiInputLabel-root": {
      fontSize: theme.typography.body2.fontSize,
      "&:not(.Mui-focused):not(.MuiInputLabel-shrink)": {
        transform: `translate(${theme.spacing(1.75)}, ${theme.spacing(1.25)}) scale(1)`,
      },
    },
  };
}

interface SlotBag {
  startAdornment?: ReactNode;
  endAdornment?: ReactNode;
  [key: string]: unknown;
}

/**
 * MUI TextField wrapper matching the legacy FilledTextField /
 * DenseFilledTextField (floating label inside the underlined/filled control,
 * dense variant for admin forms).
 */
export const Input = forwardRef<HTMLDivElement, InputProps>(function Input(
  {
    size = "md",
    label,
    helperText,
    startIcon,
    endIcon,
    variant = "outlined",
    className,
    error = false,
    fullWidth = true,
    disabled = false,
    required = false,
    id,
    inputProps,
    slotProps,
    sx,
    ...props
  },
  ref,
) {
  const dense = size === "sm";
  const inputSlot = (slotProps as { input?: SlotBag } | undefined)?.input;
  const htmlInputSlot = (slotProps as { htmlInput?: Record<string, unknown> } | undefined)?.htmlInput;

  const resolvedSlotProps = {
    ...slotProps,
    input: {
      ...inputSlot,
      startAdornment: startIcon ? (
        <InputAdornment position="start">{startIcon}</InputAdornment>
      ) : (
        inputSlot?.startAdornment
      ),
      endAdornment: endIcon ? (
        <InputAdornment position="end">{endIcon}</InputAdornment>
      ) : (
        inputSlot?.endAdornment
      ),
    },
    htmlInput: {
      ...inputProps,
      ...htmlInputSlot,
    },
  };

  return (
    <TextField
      ref={ref}
      id={id}
      variant={variant}
      label={label}
      helperText={helperText}
      error={error}
      fullWidth={fullWidth}
      disabled={disabled}
      required={required}
      className={cn(className)}
      {...(props as TextFieldProps)}
      slotProps={resolvedSlotProps as unknown as TextFieldProps["slotProps"]}
      sx={[dense ? denseSx : undefined, ...(Array.isArray(sx) ? sx : [sx])]}
    />
  );
});

Input.displayName = "Input";
