import type { ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { cn } from "@pudans/utils";
import styles from "./auth-card.module.css";

export interface AuthCardProps {
  children: ReactNode;
  className?: string;
}

/**
 * Persistent surface for every auth route. The legal route expands the same
 * card so long-form content gets room without mounting a second shell.
 */
export function AuthCard({ children, className }: AuthCardProps) {
  const { pathname } = useLocation();
  const expanded = pathname.includes("/legal");

  return (
    <div
      className={cn(styles.card, expanded && styles.expanded, className)}
      data-auth-card=""
      data-expanded={expanded ? "true" : undefined}
    >
      <div className={styles.stage} data-auth-stage="">
        {children}
      </div>
    </div>
  );
}
