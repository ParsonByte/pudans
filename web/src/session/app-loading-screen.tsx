import { Box, CircularProgress } from "@mui/material";
import { BRAND_MARK, BRAND_WORDMARK } from "../constants";

/** Full-page loader shown while the session, system state or routes load. */
export function AppLoadingScreen() {
  return (
    <Box
      role="status"
      aria-live="polite"
      aria-label={BRAND_WORDMARK}
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 2,
        minHeight: "100svh",
        backgroundColor: "background.default",
        color: "text.secondary",
      }}
    >
      <Box
        aria-hidden="true"
        sx={{
          display: "grid",
          placeItems: "center",
          width: 48,
          height: 48,
          borderRadius: 3,
          backgroundColor: "primary.main",
          color: "primary.contrastText",
          fontSize: "1.25rem",
          fontWeight: 700,
        }}
      >
        {BRAND_MARK}
      </Box>
      <CircularProgress size={22} color="inherit" />
    </Box>
  );
}
