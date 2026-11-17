import { format, parseISO } from "date-fns";

export function formatAuditTime(value: string | undefined): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? value : format(date, "MM-dd HH:mm");
}

export function formatTrendDay(value: string): string {
  const date = parseISO(value);

  return Number.isNaN(date.getTime()) ? value : format(date, "MM-dd");
}
