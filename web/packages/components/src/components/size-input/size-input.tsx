import { useId, type ReactNode } from "react";
import {
  FilledInput,
  FormControl,
  FormHelperText,
  InputAdornment,
  InputLabel,
  MenuItem,
  OutlinedInput,
  Select,
} from "@mui/material";

export type SizeUnit = "B" | "KB" | "MB" | "GB" | "TB";

export interface SizeInputProps {
  value: number;
  onChange: (bytes: number) => void;
  label?: ReactNode;
  helperText?: ReactNode;
  error?: ReactNode;
  min?: number;
  max?: number;
  allowZero?: boolean;
  units?: readonly SizeUnit[];
  disabled?: boolean;
  required?: boolean;
  size?: "sm" | "md";
  variant?: "filled" | "outlined";
  suffix?: ReactNode;
  className?: string;
  id?: string;
}

const SIZE_UNITS: ReadonlyArray<{ unit: SizeUnit; factor: number }> = [
  { unit: "B", factor: 1 },
  { unit: "KB", factor: 1024 },
  { unit: "MB", factor: 1024 * 1024 },
  { unit: "GB", factor: 1024 * 1024 * 1024 },
  { unit: "TB", factor: 1024 * 1024 * 1024 * 1024 },
];

function convertSize(bytes: number, unit: SizeUnit): number {
  const entry = SIZE_UNITS.find((item) => item.unit === unit);
  return entry ? bytes / entry.factor : bytes;
}

function pickUnit(bytes: number, allowed: readonly SizeUnit[]): SizeUnit {
  for (let index = SIZE_UNITS.length - 1; index >= 0; index -= 1) {
    const entry = SIZE_UNITS[index];
    if (allowed.includes(entry.unit) && bytes >= entry.factor && bytes % entry.factor === 0) {
      return entry.unit;
    }
  }
  return allowed[0] ?? "B";
}

/**
 * Legacy Common/SizeInput: filled (default) or dense outlined byte-size input
 * with a trailing unit Select (B/KB/MB/GB/TB, minWidth 70px, no background).
 */
export function SizeInput({
  value,
  onChange,
  label,
  helperText,
  error,
  min,
  max,
  allowZero = true,
  units = ["B", "KB", "MB", "GB", "TB"],
  disabled = false,
  required = false,
  size = "md",
  variant = "filled",
  suffix,
  className,
  id,
}: SizeInputProps) {
  const generatedId = useId();
  const inputId = id ?? "size-input-" + generatedId.replace(/:/g, "");
  const unit = pickUnit(value, units);
  const amount = convertSize(value, unit);
  const display = Number.isInteger(amount) ? String(amount) : String(Number(amount.toFixed(2)));
  const rangeError =
    (min !== undefined && value < min) || (max !== undefined && value > max)
      ? `Allowed range: ${min ?? 0} – ${max ?? "∞"} bytes`
      : undefined;
  const resolvedError = error ?? rangeError;
  const isError = Boolean(error || rangeError);
  const dense = size === "sm";

  const handleAmount = (raw: string): void => {
    const parsed = Number.parseFloat(raw);
    const nextAmount = Number.isFinite(parsed) ? parsed : 0;
    const entry = SIZE_UNITS.find((item) => item.unit === unit);
    onChange(Math.round(nextAmount * (entry?.factor ?? 1)));
  };

  const handleUnit = (nextUnit: SizeUnit): void => {
    const entry = SIZE_UNITS.find((item) => item.unit === nextUnit);
    onChange(Math.round(amount * (entry?.factor ?? 1)));
  };

  const unitSelect = (
    <Select
      variant="filled"
      size="small"
      value={unit}
      disabled={disabled || units.length <= 1}
      onChange={(event) => handleUnit(event.target.value as SizeUnit)}
      inputProps={{ "aria-label": "Unit" }}
      sx={{
        minWidth: (theme) => theme.spacing(8.75),
        backgroundColor: "initial",
        mt: variant === "filled" ? 1.75 : 0,
        ...(variant === "outlined"
          ? { "& .MuiSelect-select": { fontSize: "0.875rem" } }
          : {}),
        "& .MuiFilledInput-root": { backgroundColor: "initial", borderRadius: 0 },
        "& .MuiFilledInput-input": {
          paddingTop: (theme) => theme.spacing(0.625),
          "&:focus": { backgroundColor: "initial" },
        },
      }}
    >
      {units.map((option) => (
        <MenuItem key={option} dense value={option}>
          {option}
          {suffix}
        </MenuItem>
      ))}
    </Select>
  );

  const adornment = <InputAdornment position="end">{unitSelect}</InputAdornment>;
  const htmlInputProps = {
    step: "any",
    min: allowZero ? 0 : 1,
  };

  return (
    <FormControl
      fullWidth
      variant={variant}
      size={dense ? "small" : "medium"}
      error={isError}
      disabled={disabled}
      required={required}
      className={className}
      sx={
        variant === "outlined"
          ? (theme) => ({
              "& .MuiInputLabel-root": {
                fontSize: theme.typography.body2.fontSize,
                "&:not(.Mui-focused):not(.MuiInputLabel-shrink)": {
                  transform: `translate(${theme.spacing(1.75)}, ${theme.spacing(1.25)}) scale(1)`,
                },
              },
              "& .MuiOutlinedInput-root": { paddingTop: 0, paddingBottom: 0 },
              "& .MuiOutlinedInput-input": {
                paddingTop: theme.spacing(1.2),
                paddingBottom: theme.spacing(1.2),
                fontSize: theme.typography.body2.fontSize,
              },
            })
          : undefined
      }
    >
      {label ? <InputLabel htmlFor={inputId}>{label}</InputLabel> : null}
      {variant === "outlined" ? (
        <OutlinedInput
          id={inputId}
          type="number"
          value={display}
          label={label}
          onChange={(event) => handleAmount(event.target.value)}
          endAdornment={adornment}
          inputProps={htmlInputProps}
        />
      ) : (
        <FilledInput
          id={inputId}
          type="number"
          value={display}
          onChange={(event) => handleAmount(event.target.value)}
          endAdornment={adornment}
          inputProps={htmlInputProps}
        />
      )}
      {resolvedError ? (
        <FormHelperText error={isError}>{resolvedError}</FormHelperText>
      ) : helperText ? (
        <FormHelperText>{helperText}</FormHelperText>
      ) : null}
    </FormControl>
  );
}
