import type { ReactNode } from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import { alpha } from "@mui/material/styles";
import { cn } from "@pudans/utils";

export type StatCardTone = "brand" | "success" | "warning" | "info" | "danger";

export interface StatCardProps {
  /** Small caption above the value. */
  label: ReactNode;
  /** The headline number or text. */
  value: ReactNode;
  /** Optional supporting line below the value. */
  hint?: ReactNode;
  /** Optional leading decorative icon, tinted by the tone. */
  icon?: ReactNode;
  tone?: StatCardTone;
  className?: string;
}

const TONE_PALETTE: Record<StatCardTone, "primary" | "success" | "warning" | "info" | "error"> = {
  brand: "primary",
  success: "success",
  warning: "warning",
  info: "info",
  danger: "error",
};

/**
 * Legacy Admin Home statistics language: a BorderedCard surface (Paper, 1px
 * divider border, 12px radius, no shadow, 16px padding) holding the old
 * tinted circular icon + value/label pair, with the MUI h4 headline added for
 * the standalone tile form.
 */
export function StatCard({ label, value, hint, icon, tone = "brand", className }: StatCardProps) {
  const paletteKey = TONE_PALETTE[tone];

  return (
    <Paper
      elevation={0}
      className={cn(className)}
      sx={(theme) => ({
        display: "flex",
        alignItems: "center",
        gap: theme.spacing(2),
        minWidth: 0,
        padding: theme.spacing(2),
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: `${theme.shape.borderRadius}px`,
        backgroundColor: theme.palette.background.paper,
        boxShadow: "none",
      })}
    >
      {icon ? (
        <Avatar
          sx={(theme) => ({
            width: theme.spacing(5),
            height: theme.spacing(5),
            bgcolor: alpha(theme.palette[paletteKey].main, 0.12),
            color: theme.palette[paletteKey].main,
          })}
        >
          {icon}
        </Avatar>
      ) : null}
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary" component="div" noWrap>
          {label}
        </Typography>
        <Typography variant="h4" component="div" noWrap sx={{ fontWeight: 500 }}>
          {value}
        </Typography>
        {hint ? (
          <Typography variant="body2" color="text.secondary" component="div" noWrap>
            {hint}
          </Typography>
        ) : null}
      </Box>
    </Paper>
  );
}

StatCard.displayName = "StatCard";
