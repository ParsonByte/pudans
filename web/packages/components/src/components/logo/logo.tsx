import { forwardRef, useState, type CSSProperties, type ReactNode } from "react";
import { usePudansTheme } from "../../styles/theme-provider";
import { cn } from "../../utils/cn";
import styles from "./logo.module.css";

export interface LogoProps {
  /** Default image source (light mode). */
  src?: string;
  /** Optional dark-mode source; falls back to src. */
  darkSrc?: string;
  /** Wordmark text when no image is available. */
  name?: string;
  href?: string;
  alt?: string;
  /** Height in px; width follows the intrinsic aspect ratio. */
  height?: number;
  invertInDark?: boolean;
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
}

/**
 * Brand lockup migrated from the legacy Common/Logo. Brand artwork is injected
 * by the application; the component only owns layout, fallback wordmark and
 * light/dark source switching.
 */
export const Logo = forwardRef<HTMLAnchorElement, LogoProps>(function Logo(
  {
    src,
    darkSrc,
    name = "pudans",
    href,
    alt = "pudans",
    height = 32,
    invertInDark = false,
    className,
    style,
    children,
  },
  ref,
) {
  const { resolvedMode } = usePudansTheme();
  const [failed, setFailed] = useState(false);
  const imageSrc = resolvedMode === "dark" ? (darkSrc ?? src) : src;
  const showImage = Boolean(imageSrc) && !failed;

  const content = (
    <>
      {showImage ? (
        <img
          className={cn(styles.image, invertInDark && resolvedMode === "dark" && styles.invert)}
          src={imageSrc}
          alt={alt}
          height={height}
          onError={() => setFailed(true)}
        />
      ) : (
        <span className={styles.wordmark} style={{ fontSize: Math.max(16, height * 0.58) }}>
          {name}
        </span>
      )}
      {children}
    </>
  );

  if (href) {
    return (
      <a
        ref={ref}
        href={href}
        className={cn(styles.root, className)}
        style={style}
        aria-label={alt}
      >
        {content}
      </a>
    );
  }

  return (
    <span className={cn(styles.root, className)} style={style} role="img" aria-label={alt}>
      {content}
    </span>
  );
});

Logo.displayName = "Logo";
