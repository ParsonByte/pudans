import { type ReactNode } from "react";
import {
  FormControlLabel,
  Switch as MuiSwitch,
  type SwitchProps as MuiSwitchProps,
} from "@mui/material";
import { cn } from "../../utils/cn";
import styles from "./switch.module.css";

export type SwitchSize = "sm" | "md" | "lg";

export interface SwitchProps extends Omit<MuiSwitchProps, "size"> {
  size?: SwitchSize;
  label?: ReactNode;
  description?: ReactNode;
  labelPlacement?: "bottom" | "end" | "start" | "top";
}

/** Brand-colored switch with optional label and description. */
export function Switch({
  size = "md",
  label,
  description,
  labelPlacement = "end",
  className,
  ...props
}: SwitchProps) {
  const control = (
    <MuiSwitch
      className={cn(styles.control, styles[size])}
      size={size === "sm" ? "small" : "medium"}
      {...props}
    />
  );

  if (!label && !description) {
    return <span className={cn(styles.standalone, className)}>{control}</span>;
  }

  return (
    <FormControlLabel
      className={cn(styles.root, className)}
      control={control}
      labelPlacement={labelPlacement}
      label={
        <span className={styles.label}>
          {label ? <span className={styles.labelText}>{label}</span> : null}
          {description ? <span className={styles.description}>{description}</span> : null}
        </span>
      }
    />
  );
}
