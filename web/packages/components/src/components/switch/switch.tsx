import { type ReactNode } from "react";
import {
  Box,
  FormControlLabel,
  Switch as MuiSwitch,
  Typography,
  type SwitchProps as MuiSwitchProps,
} from "@mui/material";

export type SwitchSize = "sm" | "md" | "lg";

export interface SwitchProps extends Omit<MuiSwitchProps, "size"> {
  size?: SwitchSize;
  label?: ReactNode;
  description?: ReactNode;
  labelPlacement?: "bottom" | "end" | "start" | "top";
}

/**
 * App.tsx does not override MuiSwitch: keep MUI defaults (primary color, 34px
 * thumb track, 38px hit area) and only forward the legacy size plus optional
 * label/description.
 */
export function Switch({
  size = "md",
  label,
  description,
  labelPlacement = "end",
  className,
  ...props
}: SwitchProps) {
  const control = <MuiSwitch size={size === "sm" ? "small" : "medium"} {...props} />;

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
