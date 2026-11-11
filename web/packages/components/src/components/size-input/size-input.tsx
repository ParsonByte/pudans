import { useId, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./size-input.module.css";

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
  className?: string;
  id?: string;
}

export const SIZE_UNITS: ReadonlyArray<{ unit: SizeUnit; factor: number }> = [
  { unit: "B", factor: 1 },
  { unit: "KB", factor: 1024 },
  { unit: "MB", factor: 1024 * 1024 },
  { unit: "GB", factor: 1024 * 1024 * 1024 },
  { unit: "TB", factor: 1024 * 1024 * 1024 * 1024 },
];

export function convertSize(bytes: number, unit: SizeUnit): number {
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
 * Byte-size input with unit selection, migrated from the legacy Common/
 * SizeInput. Pure props based: no Redux, no i18n coupling.
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
  className,
  id,
}: SizeInputProps) {
  const generatedId = useId();
  const inputId = id ?? "size-input-" + generatedId.replace(/:/g, "");
  const unit = pickUnit(value, units);
  const amount = convertSize(value, unit);
  const display = Number.isInteger(amount) ? String(amount) : String(Number(amount.toFixed(2)));

  const handleAmount = (raw: string): void => {
    const parsed = Number.parseFloat(raw);
    const nextAmount = Number.isFinite(parsed) ? parsed : 0;
    const entry = SIZE_UNITS.find((item) => item.unit === unit);
    const bytes = Math.round(nextAmount * (entry?.factor ?? 1));
    onChange(bytes);
  };

  return (
    <div className={cn(styles.root, styles[size], error ? styles.invalid : undefined, className)}>
      {label ? (
        <label className={styles.label} htmlFor={inputId}>
          {label}
          {required ? (
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
      ) : null}
      <div className={styles.control}>
        <input
          id={inputId}
          type="number"
          className={styles.input}
          value={display}
          min={allowZero ? 0 : undefined}
          step="any"
          disabled={disabled}
          required={required}
          onChange={(event) => handleAmount(event.target.value)}
          aria-invalid={Boolean(error) || undefined}
        />
        <select
          className={styles.unit}
          value={unit}
          disabled={disabled || units.length <= 1}
          aria-label="Unit"
          onChange={(event) => {
            const nextUnit = event.target.value as SizeUnit;
            const entry = SIZE_UNITS.find((item) => item.unit === nextUnit);
            onChange(Math.round(amount * (entry?.factor ?? 1)));
          }}
        >
          {units.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>
      {error ? (
        <p className={styles.error} role="alert">
          {error}
        </p>
      ) : helperText ? (
        <p className={styles.helper}>{helperText}</p>
      ) : min !== undefined || max !== undefined ? (
        <p className={styles.helper}>
          Allowed range: {min ?? 0} – {max ?? "∞"} bytes
        </p>
      ) : null}
    </div>
  );
}
