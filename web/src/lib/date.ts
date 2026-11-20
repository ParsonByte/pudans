import { format, parseISO } from "date-fns";

export const BEIJING_TIME_ZONE = "Asia/Shanghai";

const BEIJING_DATE_TIME_FORMATTER = new Intl.DateTimeFormat("en-CA", {
  timeZone: BEIJING_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

type BeijingParts = {
  year: string;
  month: string;
  day: string;
  hour: string;
  minute: string;
  second: string;
};

function beijingParts(date: Date): BeijingParts {
  const parts = BEIJING_DATE_TIME_FORMATTER.formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value ?? "00";
  return {
    year: part("year"),
    month: part("month"),
    day: part("day"),
    hour: part("hour"),
    minute: part("minute"),
    second: part("second"),
  };
}

export function toDate(value: Date | string | null | undefined): Date | null {
  if (value == null) {
    return null;
  }
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function beijingDateKey(value: Date | string | null | undefined): string | null {
  const date = toDate(value);
  if (!date) {
    return null;
  }
  const parts = beijingParts(date);
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function formatBeijingDateTime(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "—";
  }
  const parts = beijingParts(date);
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function formatBeijingDateTimeSeconds(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "—";
  }
  const parts = beijingParts(date);
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

export function formatBeijingDateTimeMillis(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "—";
  }
  return `${formatBeijingDateTimeSeconds(date)}.${String(date.getMilliseconds()).padStart(3, "0")}`;
}

export function formatBeijingMonthDayTime(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "—";
  }
  const parts = beijingParts(date);
  return `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function formatBeijingClock(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "—";
  }
  const parts = beijingParts(date);
  return `${parts.hour}:${parts.minute}:${parts.second}`;
}

export function beijingHourOf(value: Date | string | null | undefined): number | null {
  const date = toDate(value);
  if (!date) {
    return null;
  }
  return Number(beijingParts(date).hour) % 24;
}

export function beijingHourKey(value: Date | string | null | undefined): string | null {
  const date = toDate(value);
  if (!date) {
    return null;
  }
  const parts = beijingParts(date);
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:00:00`;
}

export function beijingTimestamp(value: Date = new Date()): string {
  const date = toDate(value);
  if (!date) {
    return "";
  }
  const parts = beijingParts(date);
  return `${parts.year}${parts.month}${parts.day}-${parts.hour}${parts.minute}`;
}

export function serverToday(): string {
  return beijingDateKey(new Date()) ?? "";
}

function beijingOffsetMillis(utcMillis: number): number {
  const date = new Date(utcMillis);
  const parts = beijingParts(date);
  const wallClock = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour),
    Number(parts.minute),
    Number(parts.second),
    date.getMilliseconds(),
  );
  return wallClock - utcMillis;
}

function beijingWallClockToDate(value: string): Date | null {
  const parsed = parseISO(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  const wallClock = Date.UTC(
    parsed.getFullYear(),
    parsed.getMonth(),
    parsed.getDate(),
    parsed.getHours(),
    parsed.getMinutes(),
    parsed.getSeconds(),
    parsed.getMilliseconds(),
  );
  return new Date(wallClock - beijingOffsetMillis(wallClock));
}

export function startOfBeijingDay(value: Date | string): Date {
  const key = beijingDateKey(value);
  return key
    ? (beijingWallClockToDate(`${key}T00:00:00`) ?? new Date(Number.NaN))
    : new Date(Number.NaN);
}

export function beijingDateTimeInputValue(value: Date | string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return "";
  }
  const parts = beijingParts(date);
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function beijingDateTimeInputToIso(value: string): string | undefined {
  const date = beijingWallClockToDate(value);
  return date ? date.toISOString() : undefined;
}

export function formatTrendDay(value: string): string {
  const date = parseISO(value);

  return Number.isNaN(date.getTime()) ? value : format(date, "MM-dd");
}
