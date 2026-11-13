import dayjs, { type Dayjs } from "dayjs";
import localizedFormat from "dayjs/plugin/localizedFormat";
import relativeTime from "dayjs/plugin/relativeTime";

dayjs.extend(localizedFormat);
dayjs.extend(relativeTime);

export { dayjs };
export type DateInput = string | number | Date | Dayjs | null | undefined;

function toDayjs(value: DateInput): Dayjs | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const date = dayjs(value);
  return date.isValid() ? date : null;
}

export function formatDate(value: DateInput, format = "YYYY-MM-DD"): string {
  return toDayjs(value)?.format(format) ?? "—";
}

export function formatDateTimeDayjs(
  value: DateInput,
  format = "YYYY-MM-DD HH:mm:ss",
): string {
  return toDayjs(value)?.format(format) ?? "—";
}

export function fromNow(value: DateInput): string {
  return toDayjs(value)?.fromNow() ?? "—";
}

export function formatDuration(milliseconds: number): string {
  if (!Number.isFinite(milliseconds) || milliseconds < 0) {
    return "—";
  }
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  if (minutes > 0) {
    return `${minutes}m ${seconds}s`;
  }
  return `${seconds}s`;
}
