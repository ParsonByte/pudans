import type { ReactNode } from "react";
import { clsx } from "clsx";
import { useLocation } from "react-router-dom";
import styles from "./auth-card.module.css";

export interface AuthCardProps {
  children: ReactNode;
  className?: string;
}

/**
 * Persistent surface for every auth route.
 *
 * Compact routes keep the fixed 460 x 660 footprint; the legal route expands
 * the same card to 800 x 720 so long-form content gets room without mounting
 * a second shell. The stage below it is the only clipping context for the
 * animated route panels, so the surrounding page never jumps.
 */
export function AuthCard({ children, className }: AuthCardProps) {
  const { pathname } = useLocation();
  const expanded = pathname.includes("/legal");

  return (
    <div
      className={clsx(styles.card, expanded && styles.expanded, className)}
      data-auth-card=""
      data-expanded={expanded ? "true" : undefined}
    >
      <div className={styles.stage} data-auth-stage="">
        {children}
      </div>
    </div>
  );
}
