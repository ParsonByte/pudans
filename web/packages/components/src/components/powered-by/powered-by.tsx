import type { CSSProperties, ReactNode } from "react";
import { Box, Typography, type SxProps, type Theme } from "@mui/material";
import { useTheme } from "@mui/material/styles";

export interface PoweredByProps {
  label?: ReactNode;
  /** Fallback brand wordmark shown when no logo image is provided. */
  name?: ReactNode;
  href?: string;
  logoSrc?: string;
  logoDarkSrc?: string;
  logoAlt?: string;
  className?: string;
  style?: CSSProperties;
  sx?: SxProps<Theme>;
}

/**
 * Legacy Frame/PoweredBy: one full-width link row, "Powered by" (body2 / 500 /
 * action.disabled) + a 20px logo that is grayscale at 30% opacity and returns to
 * full colour on hover. The logo source is injected by the app (light/dark pair).
 */
export function PoweredBy({
  label = "Powered by",
  name = "pudans",
  href,
  logoSrc,
  logoDarkSrc,
  logoAlt = "",
  className,
  style,
  sx,
}: PoweredByProps) {
  const theme = useTheme();
  const logoSrcResolved = theme.palette.mode === "dark" ? (logoDarkSrc ?? logoSrc) : logoSrc;

  return (
    <Box className={className} style={style} sx={[{ width: "100%" }, ...(Array.isArray(sx) ? sx : sx ? [sx] : [])]}>
      <Box
        component={href ? "a" : "span"}
        href={href}
        target={href ? "_blank" : undefined}
        rel={href ? "noreferrer" : undefined}
        sx={{
          width: "100%",
          display: "flex",
          alignItems: "center",
          gap: 1,
          mb: 2,
          textDecoration: "none",
          cursor: href ? "pointer" : "default",
          "& img": { filter: "grayscale(100%)", opacity: 0.3 },
          "&:hover img": { filter: "grayscale(0%)", opacity: 1 },
        }}
      >
        <Typography variant="body2" sx={{ fontWeight: 500, color: "action.disabled" }}>
          {label}
        </Typography>
        {logoSrcResolved ? (
          <Box
            component="img"
            alt={logoAlt}
            src={logoSrcResolved}
            sx={{ height: theme.spacing(2.5), width: "auto" }}
          />
        ) : (
          <Typography variant="body2" sx={{ fontWeight: 500, color: "action.disabled" }}>
            {name}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
