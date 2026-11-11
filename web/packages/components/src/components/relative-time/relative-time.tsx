import { forwardRef } from "react";
import { Tooltip, Typography } from "@mui/material";
import type { TypographyProps } from "@mui/material";
import { formatDateTime, formatRelativeTime } from "@pudans/utils";

export interface RelativeTimeProps extends Omit<TypographyProps<"time">, "children"> {
  datetime: string | number | Date;
  /** Reference instant; defaults to now. */
  base?: string | number | Date;
  locale?: string;
  timeZone?: string;
  /** Show relative time only within this window (minutes). Default 7 days. */
  thresholdMinutes?: number;
  /** Disable the full-date tooltip. */
  tooltip?: boolean;
  /** Legacy alias for the relative window in minutes. */
  timeAgoThreshold?: number;
}

const DEFAULT_THRESHOLD_MINUTES = 7 * 24 * 60;

function toDate(value: string | number | Date): Date {
  return value instanceof Date ? value : new Date(value);
}

/**
 * Timestamp rebuilt 1:1 from the legacy Common/TimeBadge: an inline MUI
 * Typography (body1 by default, overridable via Typography props) wrapped in a
 * Tooltip that only shows the full local time while the relative label is used.
 * The relative/absolute window defaults to 7 days.
 */
export const RelativeTime = forwardRef<HTMLTimeElement, RelativeTimeProps>(function RelativeTime(
  {
    datetime,
    base,
    locale,
    timeZone,
    thresholdMinutes,
    timeAgoThreshold,
    tooltip = true,
    sx,
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
  const threshold = timeAgoThreshold ?? thresholdMinutes ?? DEFAULT_THRESHOLD_MINUTES;
  const diffMinutes = Math.abs(baseDate.getTime() - date.getTime()) / 60000;
  const showRelative = !Number.isNaN(date.getTime()) && diffMinutes < threshold;
  const label = showRelative ? formatRelativeTime(date, baseDate, locale) : full;

  return (
    <Tooltip title={tooltip && showRelative ? full : ""}>
      <Typography
        ref={ref}
        component="time"
        dateTime={iso}
        sx={{ color: "inherit", ...sx }}
        {...props}
      >
        {label}
      </Typography>
    </Tooltip>
  );
});

RelativeTime.displayName = "RelativeTime";
