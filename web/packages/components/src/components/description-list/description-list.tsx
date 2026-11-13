import type { ReactNode } from "react";
import { Box, Typography, useTheme } from "@mui/material";
import { cn } from "@pudans/shared";

export interface DescriptionItem {
  key: string;
  label: ReactNode;
  value: ReactNode;
  /** How many grid columns the item spans. */
  span?: 1 | 2 | 3;
}

export interface DescriptionListProps {
  items: readonly DescriptionItem[];
  columns?: 1 | 2 | 3;
  /** Compatibility flag: tightens the legacy 8px row rhythm to 4px. */
  dense?: boolean;
  /** Compatibility flag: wraps the list in the legacy details-card outline. */
  bordered?: boolean;
  className?: string;
}

/**
 * Label/value property list rebuilt 1:1 from the legacy FileManager Sidebar
 * InfoRow: MUI body2 label (500 / text.primary) over a body2 value
 * (text.secondary), 8px vertical rhythm, no padding, border or background.
 */
export function DescriptionList({
  items,
  columns = 1,
  dense = false,
  bordered = false,
  className,
}: DescriptionListProps) {
  const theme = useTheme();
  return (
    <Box
      component="dl"
      className={cn(className)}
      sx={{
        display: "grid",
        margin: 0,
        gap: dense ? theme.spacing(1) : theme.spacing(2),
        gridTemplateColumns:
          columns === 1 ? "minmax(0, 1fr)" : `repeat(${columns}, minmax(0, 1fr))`,
        ...(bordered
          ? {
              padding: theme.spacing(2),
              border: `var(--pd-border-width-thin) solid ${theme.palette.divider}`,
              borderRadius: theme.shape.borderRadius,
            }
          : {}),
      }}
    >
      {items.map((item) => (
        <Box
          key={item.key}
          sx={{ minWidth: 0, gridColumn: item.span ? `span ${item.span}` : undefined }}
        >
          <Typography
            component="dt"
            variant="body2"
            sx={{ fontWeight: 500, color: theme.palette.text.primary }}
          >
            {item.label}
          </Typography>
          <Typography
            component="dd"
            variant="body2"
            sx={{
              margin: 0,
              overflowWrap: "anywhere",
              color: theme.palette.text.secondary,
            }}
          >
            {item.value}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}
