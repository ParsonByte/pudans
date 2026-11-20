import { formatBeijingDateTime } from "@/lib/date";

export function formatUserDateTime(value: string | undefined): string {
  return formatBeijingDateTime(value);
}
