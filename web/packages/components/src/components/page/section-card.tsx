import type { ReactNode } from "react";
import { Box, Divider, Paper, Typography, useTheme, type SxProps, type Theme } from "@mui/material";
import { cn } from "@pudans/shared";

export interface SectionCardProps {
  title?: ReactNode;
  description?: ReactNode;
  /** Right-aligned header content (link, caption, button). */
  action?: ReactNode;
  /** Optional leading icon rendered next to the title. */
  icon?: ReactNode;
  children?: ReactNode;
  className?: string;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Admin Home StyledPaper / AdminCard surface:
 * - Paper, elevation 0, 1px divider border, shape.borderRadius (12px), no shadow
 * - 24px padding, full height, flex column
 * - subtitle1 / 500 section title, optional action on the right
 * - Divider with margin-top 8px / margin-bottom 16px
 */
export function SectionCard({
  title,
  description,
  action,
  icon,
  children,
  className,
  sx,
}: SectionCardProps) {
  const theme = useTheme();

  return (
    <Paper
      elevation={0}
      className={cn(className)}
      sx={[
        {
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          height: "100%",
          padding: 3,
          border: `1px solid ${theme.palette.divider}`,
          borderRadius: `${theme.shape.borderRadius}px`,
          backgroundColor: theme.palette.background.paper,
          boxShadow: "none",
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {title ? (
        <Typography
          variant="subtitle1"
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 1,
            fontWeight: 500,
            minWidth: 0,
          }}
        >
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
            {icon ? <Box sx={{ display: "flex", color: "action.active", flex: "0 0 auto" }}>{icon}</Box> : null}
            <Box component="span" sx={{ minWidth: 0, overflow: "hidden", textOverflow: "ellipsis" }}>
              {title}
            </Box>
          </Box>
          {action ? <Box sx={{ flex: "0 0 auto", display: "flex", alignItems: "center" }}>{action}</Box> : null}
        </Typography>
      ) : null}
      {description ? (
        <Typography variant="body2" sx={{ mt: 1, color: "text.secondary", lineHeight: 1.66 }}>
          {description}
        </Typography>
      ) : null}
      {title || description ? <Divider sx={{ mb: 2, mt: 1 }} /> : null}
      {children ? <Box sx={{ flexGrow: 1, minWidth: 0 }}>{children}</Box> : null}
    </Paper>
  );
}
