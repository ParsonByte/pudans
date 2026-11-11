import { type ReactNode } from "react";
import {
  Box,
  Checkbox as MuiCheckbox,
  FormControlLabel,
  Typography,
  type CheckboxProps as MuiCheckboxProps,
} from "@mui/material";

export type CheckboxSize = "sm" | "md" | "lg";

export interface CheckboxProps extends Omit<MuiCheckboxProps, "size"> {
  size?: CheckboxSize;
  label?: ReactNode;
  description?: ReactNode;
  labelPlacement?: "bottom" | "end" | "start" | "top";
}

/**
 * App.tsx keeps MUI defaults for Checkbox (primary color, 9px padding hit
 * area, 4px ripple radius); this wrapper only forwards the legacy size prop and
 * the optional label/description copy.
 */
export function Checkbox({
  size = "md",
  label,
  description,
  labelPlacement = "end",
  className,
  ...props
}: CheckboxProps) {
  const control = <MuiCheckbox size={size === "sm" ? "small" : "medium"} {...props} />;

  if (!label && !description) {
    return (
      <Box component="span" className={className} sx={{ display: "inline-flex" }}>
        {control}
      </Box>
    );
  }

  return (
    <FormControlLabel
      className={className}
      control={control}
      labelPlacement={labelPlacement}
      label={
        <Box component="span" sx={{ display: "inline-flex", flexDirection: "column" }}>
          {label ? (
            <Typography variant="body1" component="span">
              {label}
            </Typography>
          ) : null}
          {description ? (
            <Typography variant="caption" component="span" sx={{ color: "text.secondary" }}>
              {description}
            </Typography>
          ) : null}
        </Box>
      }
    />
  );
}
