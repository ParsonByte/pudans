import type { ReactNode } from "react";
import { MuiOtpInput, type MuiOtpInputProps } from "mui-one-time-password-input";
import type { TextFieldProps } from "@mui/material";
import { clsx } from "clsx";
import styles from "./otp-input.module.css";

export type OtpInputSize = "sm" | "md" | "lg";

export interface OtpInputProps extends Omit<MuiOtpInputProps, "TextFieldsProps"> {
  size?: OtpInputSize;
  error?: boolean;
  disabled?: boolean;
  helperText?: ReactNode;
  TextFieldsProps?: MuiOtpInputProps["TextFieldsProps"];
}

/** One-time-password input built on mui-one-time-password-input. */
export function OtpInput({
  size = "md",
  error = false,
  helperText,
  className,
  TextFieldsProps,
  disabled,
  ...props
}: OtpInputProps) {
  const resolveFieldProps = (index: number): TextFieldProps => {
    const base = typeof TextFieldsProps === "function" ? TextFieldsProps(index) : TextFieldsProps;
    return {
      size: "small",
      ...base,
      error: error || base?.error,
      disabled: disabled ?? base?.disabled,
      className: clsx(styles.field, base?.className),
      slotProps: {
        ...base?.slotProps,
        htmlInput: {
          "aria-label": `Digit ${index + 1}`,
          inputMode: "numeric",
          ...base?.slotProps?.htmlInput,
        },
      },
    };
  };

  return (
    <div className={clsx(styles.root, className)}>
      <MuiOtpInput
        {...props}
        className={clsx(styles.input, styles[size])}
        TextFieldsProps={resolveFieldProps}
      />
      {helperText ? (
        <div className={clsx(styles.helper, error && styles.helperError)}>{helperText}</div>
      ) : null}
    </div>
  );
}
