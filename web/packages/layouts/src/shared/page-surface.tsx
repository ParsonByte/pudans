import { type ReactNode } from "react";
import { Box, IconButton, Tooltip, Typography, useTheme, styled } from "@mui/material";
import { Icon } from "@pudans/components";

/**
 * Legacy Frame/RadiusFrame + Pages/PageContainer surface:
 * Paper card, 1px divider border (desktop), 12px radius (theme.shape), py 32px,
 * mb 8px, scrolls internally. Below sm: squared corners, no border, no margin.
 */
const PageSurfaceRoot = styled(Box)(({ theme }) => ({
  flexGrow: 1,
  minWidth: 0,
  paddingTop: theme.spacing(4),
  paddingBottom: theme.spacing(4),
  marginBottom: theme.spacing(1),
  overflow: "auto",
  borderRadius: theme.shape.borderRadius,
  backgroundColor: theme.palette.background.paper,
  border: `1px solid ${theme.palette.divider}`,
  [theme.breakpoints.down("sm")]: {
    marginBottom: 0,
    borderRadius: 0,
    border: "none",
  },
}));

/** Page surface wrapper (see PageSurfaceRoot). */
export function PageSurface({
  children,
  className,
}: {
  children?: ReactNode;
  className?: string;
}) {
  return <PageSurfaceRoot className={className}>{children}</PageSurfaceRoot>;
}

export interface PageHeaderRowProps {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  loading?: boolean;
  onRefresh?: () => void;
  refreshLabel?: string;
}

/**
 * Legacy Pages/PageHeader: one flex row with h4/600 title, optional refresh
 * IconButton, flex spacer and right-aligned secondary actions; mb 32px.
 */
export function PageHeaderRow({
  title,
  description,
  actions,
  loading,
  onRefresh,
  refreshLabel = "Refresh",
}: PageHeaderRowProps) {
  const theme = useTheme();
  return (
    <Box sx={{ display: "flex", alignItems: "center", mb: 4, px: 3 }}>
      <Box sx={{ minWidth: 0 }}>
        {title ? (
          <Typography variant="h4" noWrap sx={{ fontWeight: 600 }}>
            {title}
          </Typography>
        ) : null}
        {description ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {description}
          </Typography>
        ) : null}
      </Box>
      {onRefresh ? (
        <Tooltip title={refreshLabel}>
          <span>
            <IconButton onClick={onRefresh} disabled={loading} sx={{ ml: 1 }}>
              <Icon name="mdi:refresh" size={theme.spacing(3)} />
            </IconButton>
          </span>
        </Tooltip>
      ) : null}
      <Box sx={{ flexGrow: 1 }} />
      {actions ? (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexShrink: 0 }}>
          {actions}
        </Box>
      ) : null}
    </Box>
  );
}