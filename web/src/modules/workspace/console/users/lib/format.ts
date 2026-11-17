import { format } from "date-fns";

export function formatUserDateTime(value: string | undefined): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? value : format(date, "yyyy-MM-dd HH:mm");
}
