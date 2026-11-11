import type { DateInput } from "./date";

/**
 * Local-time formatting migrated from the legacy frontend util/datetime.
 * Kept i18n-agnostic: the caller supplies locale/timezone (or accepts the
 * environment defaults) instead of a global i18next singleton.
 */
export function formatLocalTime(
  value: DateInput,
  options: { locale?: string; timeZone?: string; ignoreTimeZone?: boolean } = {},
): string {
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  const date = value instanceof Date ? value : new Date(value as string | number);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  try {
    return new Intl.DateTimeFormat(options.locale, {
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "numeric",
      second: "numeric",
      timeZone: options.ignoreTimeZone ? "UTC" : options.timeZone,
    }).format(date);
  } catch {
    return date.toISOString();
  }
}

export function formatDurationHuman(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) {
    return "—";
  }
  const seconds = Math.floor(totalSeconds);
  if (seconds < 60) {
    return seconds + "s";
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return minutes + "m " + (seconds % 60) + "s";
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return hours + "h " + (minutes % 60) + "m";
  }
  const days = Math.floor(hours / 24);
  return days + "d " + (hours % 24) + "h";
}
