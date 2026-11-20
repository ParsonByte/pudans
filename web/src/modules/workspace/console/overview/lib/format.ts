import { formatBeijingMonthDayTime } from "@/lib/date";

export function formatAuditTime(value: string | undefined): string {
  return formatBeijingMonthDayTime(value);
}
