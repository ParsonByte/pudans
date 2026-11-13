const BYTE_UNITS = ["B", "KB", "MB", "GB", "TB", "PB"] as const;

export function formatBytes(bytes: number, decimals = 1): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return "—";
  }
  if (bytes === 0) {
    return "0 B";
  }

  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), BYTE_UNITS.length - 1);
  const value = bytes / 1024 ** exponent;
  const unit = BYTE_UNITS[exponent] ?? "B";
  return `${value.toFixed(exponent === 0 ? 0 : decimals)} ${unit}`;
}

export function formatDateTime(
  value: string | number | Date,
  locale?: string,
  options: Intl.DateTimeFormatOptions = {
    dateStyle: "medium",
    timeStyle: "short",
  },
): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return new Intl.DateTimeFormat(locale, options).format(date);
}

export function formatRelativeTime(
  value: string | number | Date,
  base: string | number | Date = Date.now(),
  locale?: string,
): string {
  const date = value instanceof Date ? value : new Date(value);
  const now = base instanceof Date ? base : new Date(base);
  if (Number.isNaN(date.getTime()) || Number.isNaN(now.getTime())) {
    return "—";
  }

  const diffSeconds = Math.round((date.getTime() - now.getTime()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ["year", 60 * 60 * 24 * 365],
    ["month", 60 * 60 * 24 * 30],
    ["week", 60 * 60 * 24 * 7],
    ["day", 60 * 60 * 24],
    ["hour", 60 * 60],
    ["minute", 60],
    ["second", 1],
  ];

  for (const [unit, seconds] of units) {
    if (Math.abs(diffSeconds) >= seconds || unit === "second") {
      return formatter.format(Math.round(diffSeconds / seconds), unit);
    }
  }

  return "—";
}

export function truncate(value: string, maxLength: number, ellipsis = "…"): string {
  if (maxLength < 0) {
    return value;
  }
  return value.length > maxLength
    ? `${value.slice(0, Math.max(0, maxLength - ellipsis.length))}${ellipsis}`
    : value;
}
