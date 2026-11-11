import type { ReactNode } from "react";
import { FormHelperText, type TextFieldProps } from "@mui/material";
import { styled } from "@mui/material/styles";
import { MuiOtpInput, type MuiOtpInputProps } from "mui-one-time-password-input";

export type OtpInputSize = "sm" | "md" | "lg";

export interface OtpInputProps extends Omit<MuiOtpInputProps, "TextFieldsProps"> {
  size?: OtpInputSize;
  error?: boolean;
  disabled?: boolean;
  helperText?: ReactNode;
  TextFieldsProps?: MuiOtpInputProps["TextFieldsProps"];
}

/** Legacy Phase2FA: flex row of 6 outlined TextFields, gap 8px, centered. */
const StyledOtpInput = styled(MuiOtpInput)(({ theme }) => ({
  display: "flex",
  gap: theme.spacing(1),
  maxWidth: theme.spacing(81.25),
  marginInline: "auto",
}));

/** One-time-password input matching the legacy 2FA login phase. */
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
      ...base,
      size: size === "sm" ? "small" : "medium",
      error: error || base?.error,
      disabled: disabled ?? base?.disabled,
      slotProps: {
        ...base?.slotProps,
        htmlInput: {
          "aria-label": `Digit ${index + 1}`,
          inputMode: "numeric",
          ...(base?.slotProps?.htmlInput as Record<string, unknown> | undefined),
        },
      },
    } as TextFieldProps;
  };

  return (
    <div className={className}>
      <StyledOtpInput {...props} TextFieldsProps={resolveFieldProps} />
      {helperText ? <FormHelperText error={error}>{helperText}</FormHelperText> : null}
    </div>
  );
}
