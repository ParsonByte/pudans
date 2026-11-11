import { forwardRef, useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import {
  Box,
  Skeleton,
  Typography,
  type SxProps,
  type Theme,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";

export interface LogoProps {
  /** Default image source (light mode). */
  src?: string;
  /** Optional dark-mode source; falls back to src. Mirrors the legacy logo/logo_light pair. */
  darkSrc?: string;
  /** Wordmark text used only when no image source is available. */
  name?: string;
  href?: string;
  alt?: string;
  /** Image height in px; the width follows the intrinsic aspect ratio. */
  height?: number;
  invertInDark?: boolean;
  className?: string;
  style?: CSSProperties;
  /** Applied to the img / skeleton exactly like the legacy `sx` pass-through. */
  sx?: SxProps<Theme>;
  children?: ReactNode;
}

/**
 * Legacy Common/Logo: light/dark source switch, a wave Skeleton while the image
 * decodes, then the img shown with drag/select disabled. Styling (width, max
 * height, object-fit, object-position) is owned by the caller through `sx`,
 * because the legacy usages sized the img itself (DrawerHeader 160x35,
 * HeadlessFrame 40% x 40px).
 */
export const Logo = forwardRef<HTMLAnchorElement, LogoProps>(function Logo(
  {
    src,
    darkSrc,
    name = "pudans",
    href,
    alt = "Logo",
    height = 32,
    invertInDark = false,
    className,
    style,
    sx,
    children,
  },
  ref,
) {
  const theme = useTheme();
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const imageSrc = theme.palette.mode === "dark" ? (darkSrc ?? src) : src;
  const showImage = Boolean(imageSrc) && !failed;

  useEffect(() => {
    setFailed(false);
    setLoaded(false);
    const image = imageRef.current;
    if (image?.complete && image.naturalWidth > 0) {
      setLoaded(true);
    }
  }, [imageSrc]);

  const callerSx = Array.isArray(sx) ? sx : sx ? [sx] : [];
  const mediaSx: SxProps<Theme> = [
    {
      display: loaded ? "block" : "none",
      width: "100%",
      height,
      objectFit: "contain",
      objectPosition: "left",
      filter: invertInDark && theme.palette.mode === "dark" ? "invert(1)" : undefined,
      userSelect: "none",
      WebkitUserDrag: "none",
    },
    ...callerSx,
  ];

  const content = (
    <>
      {showImage && !loaded ? (
        <Skeleton
          animation="wave"
          variant="rounded"
          sx={[{ width: "100%", height }, ...callerSx]}
        />
      ) : null}
      {showImage ? (
        <Box
          component="img"
          ref={imageRef}
          src={imageSrc}
          alt={alt}
          draggable={false}
          onLoad={() => setLoaded(true)}
          sx={mediaSx}
        />
      ) : (
        <Typography
          component="span"
          variant="h6"
          sx={[{ fontWeight: 700, lineHeight: 1, color: "primary.main" }, ...callerSx]}
        >
          {name}
        </Typography>
      )}
      {children}
    </>
  );

  const rootSx = {
    display: "inline-flex",
    alignItems: "center",
    textDecoration: "none",
    color: "inherit",
  } as const;

  if (href) {
    return (
      <Box ref={ref} component="a" href={href} className={className} style={style} sx={rootSx}>
        {content}
      </Box>
    );
  }

  return (
    <Box ref={ref} component="span" className={className} style={style} sx={rootSx}>
      {content}
    </Box>
  );
});

Logo.displayName = "Logo";
