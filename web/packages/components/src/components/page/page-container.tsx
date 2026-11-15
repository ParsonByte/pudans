import type { ReactNode } from "react";
import {
  Box,
  Container,
  useMediaQuery,
  useTheme,
  type ContainerProps,
  type SxProps,
  type Theme,
} from "@mui/material";
import { cn } from "@pudans/shared";

export interface PageContainerProps {
  children: ReactNode;
  /** Mirrors the Inner Container of the reference Pages/PageContainer usage. */
  maxWidth?: ContainerProps["maxWidth"];
  disableGutters?: boolean;
  className?: string;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Pages/PageContainer + RadiusFrame:
 * - flexGrow 1, padding 32px vertical, overflow auto
 * - 8px bottom margin on desktop only
 * - background.paper surface with a 1px divider border and shape.borderRadius
 *   (12px) corners; square and borderless below the sm breakpoint
 * - inner MUI Container carries the page content (default maxWidth xl, the
 *   reference admin pages; user-centre pages pass lg).
 */
export function PageContainer({
  children,
  maxWidth = "xl",
  disableGutters = false,
  className,
  sx,
}: PageContainerProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  return (
    <Box
      className={cn(className)}
      sx={[
        {
          flexGrow: 1,
          minHeight: 0,
          marginBottom: isMobile ? 0 : 1,
          paddingY: 4,
          overflow: "auto",
          borderRadius: isMobile ? 0 : `${theme.shape.borderRadius}px`,
          backgroundColor: theme.palette.background.paper,
          border: isMobile ? 0 : `1px solid ${theme.palette.divider}`,
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      <Container maxWidth={maxWidth} disableGutters={disableGutters}>
        {children}
      </Container>
    </Box>
  );
}
