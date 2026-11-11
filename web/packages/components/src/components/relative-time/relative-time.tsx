import { forwardRef, type HTMLAttributes } from "react";
import { formatDateTime, formatRelativeTime } from "@pudans/shared";
import { cn } from "../../utils/cn";
import styles from "./relative-time.module.css";

export interface RelativeTimeProps extends Omit<HTMLAttributes<HTMLTimeElement>, "children"> {
  datetime: string | number | Date;
  /** Reference instant; defaults to now. */
  base?: string | number | Date;
  locale?: string;
  timeZone?: string;
  /** Show relative time only within this window (minutes). Default 7 days. */
  thresholdMinutes?: number;
  /** Disable the full-date tooltip. */
  tooltip?: boolean;
}

const DEFAULT_THRESHOLD_MINUTES = 7 * 24 * 60;

function toDate(value: string | number | Date): Date {
  return value instanceof Date ? value : new Date(value);
}

/**
 * Relative timestamp migrated from the legacy TimeBadge. Uses Intl instead of
 * timeago-react, keeps a full localized timestamp in the tooltip and stays
 * timezone-aware for future per-user timezone settings.
 */
export const RelativeTime = forwardRef<HTMLTimeElement, RelativeTimeProps>(function RelativeTime(
  {
    datetime,
    base,
    locale,
    timeZone,
    thresholdMinutes = DEFAULT_THRESHOLD_MINUTES,
    tooltip = true,
    className,
    ...props
  },
  ref,
) {
  const date = toDate(datetime);
  const baseDate = base === undefined ? new Date() : toDate(base);
  const iso = Number.isNaN(date.getTime()) ? undefined : date.toISOString();
  const full = formatDateTime(date, locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone,
  });
  const diffMinutes = Math.abs(baseDate.getTime() - date.getTime()) / 60000;
  const showRelative = !Number.isNaN(date.getTime()) && diffMinutes < thresholdMinutes;
  const label = showRelative ? formatRelativeTime(date, baseDate, locale) : full;

  return (
    <time
      ref={ref}
      dateTime={iso}
      title={tooltip ? full : undefined}
      className={cn(styles.root, className)}
      {...props}
    >
      {label}
    </time>
  );
});

RelativeTime.displayName = "RelativeTime";
