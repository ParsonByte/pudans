import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./stat-card.module.css";

export type StatCardTone = "brand" | "success" | "warning" | "info" | "danger";

export interface StatCardProps {
  /** Small caption above the value. */
  label: ReactNode;
  /** The headline number or text. */
  value: ReactNode;
  /** Optional supporting line below the value. */
  hint?: ReactNode;
  /** Optional leading decorative icon, tinted by the tone. */
  icon?: ReactNode;
  tone?: StatCardTone;
  className?: string;
}

/**
 * Dashboard statistic tile: a quiet surface card with a labelled value and an
 * optional tinted icon chip. Purely presentational — data fetching stays with
 * the caller.
 */
export function StatCard({ label, value, hint, icon, tone = "brand", className }: StatCardProps) {
  return (
    <div className={cn(styles.card, className)}>
      <div className={styles.top}>
        <span className={styles.label}>{label}</span>
        {icon ? (
          <span className={cn(styles.icon, styles[tone])} aria-hidden="true">
            {icon}
          </span>
        ) : null}
      </div>
      <span className={styles.value}>{value}</span>
      {hint ? <span className={styles.hint}>{hint}</span> : null}
    </div>
  );
}

StatCard.displayName = "StatCard";
