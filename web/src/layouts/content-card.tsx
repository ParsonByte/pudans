import type { ReactNode } from "react";
import { Box, Stack, Typography } from "@mui/material";
import { Icon } from "@pudans/components";

export interface ContentCardProps {
  title: string;
  description?: string;
  icon?: string;
  action?: ReactNode;
  children?: ReactNode;
}

/** Bordered dashboard card matching the reference content grid. */
export function ContentCard({ title, description, icon, action, children }: ContentCardProps) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        height: "100%",
        p: 3,
        border: 1,
        borderColor: "divider",
        borderRadius: 3,
        backgroundColor: "background.paper",
      }}
    >
      <Stack direction="row" spacing={1.25} sx={{ alignItems: "center", minWidth: 0 }}>
        {icon ? <Icon name={icon} size={22} /> : null}
        <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 600, minWidth: 0 }} noWrap>
          {title}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {action}
      </Stack>
      {description ? (
        <Typography sx={{ mt: 1, color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.6 }}>
          {description}
        </Typography>
      ) : null}
      {children ? <Box sx={{ mt: 2.5, flexGrow: 1, minWidth: 0 }}>{children}</Box> : null}
    </Box>
  );
}

export interface DescriptionRowProps {
  label: string;
  value: ReactNode;
}

export function DescriptionRow({ label, value }: DescriptionRowProps) {
  return (
    <Stack
      direction="row"
      spacing={2}
      sx={{
        alignItems: "baseline",
        justifyContent: "space-between",
        py: 1,
        borderBottom: 1,
        borderColor: "divider",
        "&:last-of-type": { borderBottom: 0 },
      }}
    >
      <Typography sx={{ color: "text.secondary", fontSize: "0.875rem" }}>{label}</Typography>
      <Typography sx={{ fontSize: "0.875rem", fontWeight: 500, textAlign: "right", minWidth: 0 }} noWrap>
        {value}
      </Typography>
    </Stack>
  );
}
