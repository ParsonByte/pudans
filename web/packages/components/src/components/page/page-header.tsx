import type { ReactNode } from "react";
import { Box, IconButton, Tooltip, Typography, type SxProps, type Theme } from "@mui/material";
import { cn } from "@pudans/shared";
import { Icon } from "../icon";

export interface PageHeaderProps {
  title: ReactNode;
  /** Optional refresh button next to the title, as in the reference PageHeader. */
  onRefresh?: () => void;
  refreshLabel?: string;
  loading?: boolean;
  /** Right-aligned actions (for example a primary button). */
  secondaryAction?: ReactNode;
  className?: string;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Pages/PageHeader: a 32px bottom margin, h4 title at weight 600, an
 * optional refresh IconButton, then a flex spacer and right-aligned actions.
 */
export function PageHeader({
  title,
  onRefresh,
  refreshLabel = "Refresh",
  loading = false,
  secondaryAction,
  className,
  sx,
}: PageHeaderProps) {
  return (
    <Box className={cn(className)} sx={[{ marginBottom: 4 }, ...(Array.isArray(sx) ? sx : [sx])]}>
      <Box sx={{ display: "flex", alignItems: "center", minWidth: 0 }}>
        <Typography variant="h4" sx={{ fontWeight: 600, minWidth: 0 }}>
          {title}
        </Typography>
        {onRefresh ? (
          <Tooltip title={refreshLabel}>
            <span>
              <IconButton onClick={onRefresh} disabled={loading} sx={{ marginLeft: 1 }} aria-label={refreshLabel}>
                <Icon name="pd:ArrowClockwiseFilled" size={24} />
              </IconButton>
            </span>
          </Tooltip>
        ) : null}
        <Box sx={{ flexGrow: 1 }} />
        {secondaryAction}
      </Box>
    </Box>
  );
}
