import { format, parseISO } from "date-fns";

export function formatTrendDay(value: string): string {
  const date = parseISO(value);

  return Number.isNaN(date.getTime()) ? value : format(date, "MM-dd");
}
