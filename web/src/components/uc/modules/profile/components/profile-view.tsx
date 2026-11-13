import { Box, Typography } from "@mui/material";
import { useProfilePage } from "../composables/useProfilePage";

export function UcProfileView() {
  const { title, description } = useProfilePage();
  return (
    <Box sx={{ p: { xs: 3, md: 4 }, border: 1, borderColor: "divider", borderRadius: 3, bgcolor: "background.paper" }}>
      <Typography component="h2" sx={{ fontWeight: 600, fontSize: "1.25rem" }}>{title}</Typography>
      <Typography sx={{ mt: 1, color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.7 }}>{description}</Typography>
    </Box>
  );
}
