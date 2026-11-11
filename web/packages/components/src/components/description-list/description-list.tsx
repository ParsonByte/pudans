import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./description-list.module.css";

export interface DescriptionItem {
  key: string;
  label: ReactNode;
  value: ReactNode;
  /** How many grid columns the item spans. */
  span?: 1 | 2 | 3;
}

export interface DescriptionListProps {
  items: readonly DescriptionItem[];
  columns?: 1 | 2 | 3;
  dense?: boolean;
  bordered?: boolean;
  className?: string;
}

/**
 * Label/value property list migrated from the legacy file sidebar InfoRow /
 * Details panels; used by detail drawers, dialogs and settings summaries.
 */
export function DescriptionList({
  items,
  columns = 1,
  dense = false,
  bordered = false,
  className,
}: DescriptionListProps) {
  return (
    <dl
      className={cn(
        styles.root,
        styles["cols" + columns],
        dense && styles.dense,
        bordered && styles.bordered,
        className,
      )}
    >
      {items.map((item) => (
        <div
          key={item.key}
          className={styles.item}
          style={item.span ? { gridColumn: "span " + item.span } : undefined}
        >
          <dt className={styles.label}>{item.label}</dt>
          <dd className={styles.value}>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
