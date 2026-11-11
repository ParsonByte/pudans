import type { ReactNode } from "react";
import type { Dayjs } from "dayjs";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { DatePicker as MuiDatePicker } from "@mui/x-date-pickers/DatePicker";
import { DateTimePicker as MuiDateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import { TimePicker as MuiTimePicker } from "@mui/x-date-pickers/TimePicker";

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
 * Dayjs-backed MUI X picker. Legacy usage kept MUI defaults (outlined medium
 * TextField, floating label, Paper radius 12 / elevation 8), only adding
 * fullWidth + small size in dense admin forms.
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
  const pickerProps = {
    value,
    defaultValue,
    onChange: (next: Dayjs | null) => onChange?.(next),
    label,
    disabled,
    minDate,
    maxDate,
    format,
    className,
    slotProps: {
      textField: {
        fullWidth,
        error,
        helperText,
        size: (size === "sm" ? "small" : "medium") as "small" | "medium",
      },
    },
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      {mode === "datetime" ? (
        <MuiDateTimePicker {...pickerProps} />
      ) : mode === "time" ? (
        <MuiTimePicker {...pickerProps} />
      ) : (
        <MuiDatePicker {...pickerProps} />
      )}
    </LocalizationProvider>
  );
}
