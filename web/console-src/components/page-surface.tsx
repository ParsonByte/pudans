import type { ReactNode } from "react";
import { Box, Typography } from "@mui/material";

export interface PageSurfaceProps {
  title: string;
  description: string;
  children?: ReactNode;
}

export function PageSurface({ title, description, children }: PageSurfaceProps) {
  return (
    <Box
      component="section"
      sx={{
        p: { xs: 2.5, md: 3.5 },
        border: 1,
        borderColor: "divider",
        borderRadius: 3,
        backgroundColor: "background.paper",
      }}
    >
      <Typography component="h1" sx={{ fontWeight: 600, fontSize: "1.5rem", letterSpacing: "-0.02em" }}>
        {title}
      </Typography>
      <Typography sx={{ mt: 1, color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.7 }}>
        {description}
      </Typography>
      {children ? <Box sx={{ mt: 3 }}>{children}</Box> : null}
    </Box>
  );
}
