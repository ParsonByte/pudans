import type { ReactNode } from "react";
import { Box, Typography } from "@mui/material";

export interface PageSurfaceProps {
  description: string;
  children?: ReactNode;
}

/** dashboard bordered-card content surface. */
export function PageSurface({ description, children }: PageSurfaceProps) {
  return (
    <Box
      component="section"
      sx={{
        p: 2,
        border: 1,
        borderColor: "divider",
        borderRadius: 3,
        backgroundColor: "background.paper",
      }}
    >
      <Typography sx={{ color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.7 }}>
        {description}
      </Typography>
      {children ? <Box sx={{ mt: 2 }}>{children}</Box> : null}
    </Box>
  );
}
