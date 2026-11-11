import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./powered-by.module.css";

export interface PoweredByProps {
  label?: ReactNode;
  name?: ReactNode;
  href?: string;
  logoSrc?: string;
  logoAlt?: string;
  className?: string;
}

/**
 * Footer attribution migrated from the legacy Frame/PoweredBy. Brand-agnostic:
 * the application supplies label/name/logo.
 */
export function PoweredBy({
  label = "Powered by",
  name = "pudans",
  href,
  logoSrc,
  logoAlt = "",
  className,
}: PoweredByProps) {
  const content = (
    <>
      <span className={styles.label}>{label}</span>
      {logoSrc ? <img className={styles.logo} src={logoSrc} alt={logoAlt} height={18} /> : null}
      <span className={styles.name}>{name}</span>
    </>
  );

  return (
    <div className={cn(styles.root, className)}>
      {href ? (
        <a className={styles.link} href={href} target="_blank" rel="noreferrer">
          {content}
        </a>
      ) : (
        <span className={styles.link}>{content}</span>
      )}
    </div>
  );
}
