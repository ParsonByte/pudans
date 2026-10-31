import { type ReactNode } from "react";
import {
  Checkbox as MuiCheckbox,
  FormControlLabel,
  type CheckboxProps as MuiCheckboxProps,
} from "@mui/material";
import { clsx } from "clsx";
import styles from "./checkbox.module.css";

export type CheckboxSize = "sm" | "md" | "lg";

export interface CheckboxProps extends Omit<MuiCheckboxProps, "size"> {
  size?: CheckboxSize;
  label?: ReactNode;
  description?: ReactNode;
  labelPlacement?: "bottom" | "end" | "start" | "top";
}

/** Brand-colored checkbox with an optional label and supporting text. */
export function Checkbox({
  size = "md",
  label,
  description,
  labelPlacement = "end",
  className,
  ...props
}: CheckboxProps) {
  const control = <MuiCheckbox className={clsx(styles.control, styles[size])} {...props} />;

  if (!label && !description) {
    return <span className={clsx(styles.standalone, className)}>{control}</span>;
  }

  return (
    <FormControlLabel
      className={clsx(styles.root, className)}
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
