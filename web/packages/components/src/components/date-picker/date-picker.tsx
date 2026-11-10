import type { ReactNode } from "react";
import type { Dayjs } from "dayjs";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { DatePicker as MuiDatePicker } from "@mui/x-date-pickers/DatePicker";
import { DateTimePicker as MuiDateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import { TimePicker as MuiTimePicker } from "@mui/x-date-pickers/TimePicker";
import { cn } from "../../utils/cn";
import styles from "./date-picker.module.css";

export type DatePickerMode = "date" | "datetime" | "time";
export type DatePickerSize = "sm" | "md" | "lg";

export interface DatePickerProps {
  mode?: DatePickerMode;
  value?: Dayjs | null;
  defaultValue?: Dayjs | null;
  onChange?: (value: Dayjs | null) => void;
  label?: ReactNode;
  size?: DatePickerSize;
  error?: boolean;
  helperText?: ReactNode;
  fullWidth?: boolean;
  disabled?: boolean;
  minDate?: Dayjs;
  maxDate?: Dayjs;
  format?: string;
  className?: string;
}

/**
 * Dayjs-backed date, datetime and time picker. The adapter is configured inside
 * the component so plugin authors can use it without extra setup.
 */
export function DatePicker({
  mode = "date",
  value,
  defaultValue,
  onChange,
  label,
  size = "md",
  error = false,
  helperText,
  fullWidth = true,
  disabled = false,
  minDate,
  maxDate,
  format,
  className,
}: DatePickerProps) {
  const commonProps = {
    value,
    defaultValue,
    onChange: (next: Dayjs | null) => onChange?.(next),
    label,
    disabled,
    minDate,
    maxDate,
    format,
    className: cn(styles.root, styles[size], className),
    slotProps: {
      textField: {
        size: "small" as const,
        fullWidth,
        error,
        helperText,
        className: styles.field,
      },
    },
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      {mode === "datetime" ? (
        <MuiDateTimePicker {...commonProps} />
      ) : mode === "time" ? (
        <MuiTimePicker {...commonProps} />
      ) : (
        <MuiDatePicker {...commonProps} />
      )}
    </LocalizationProvider>
  );
}
