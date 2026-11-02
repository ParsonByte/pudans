import type { ReactNode } from "react";
import { clsx } from "clsx";
import { Tabs } from "../../components/tabs";
import styles from "./page-container.module.css";

export interface PageTab {
  key: string;
  label: ReactNode;
}

export interface PageContainerProps {
  title: ReactNode;
  actions?: ReactNode;
  tabs?: readonly PageTab[];
  activeTab?: string;
  onTabChange?: (key: string) => void;
  children?: ReactNode;
  className?: string;
}

/**
 * Standard page skeleton for shell-hosted content: a paper card whose header
 * holds the page title and actions, an optional tab strip, and a scrollable
 * body that owns the page padding and the vertical rhythm between sections.
 */
export function PageContainer({
  title,
  actions,
  tabs,
  activeTab,
  onTabChange,
  children,
  className,
}: PageContainerProps) {
  return (
    <section className={clsx(styles.paper, className)}>
      <header className={styles.header}>
        <div className={styles.heading}>
          <h1 className={styles.title}>{title}</h1>
        </div>
        {actions ? <div className={styles.actions}>{actions}</div> : null}
      </header>

      {tabs?.length ? (
        <div className={styles.tabs}>
          <Tabs
            items={tabs}
            value={activeTab}
            onValueChange={onTabChange}
          />
        </div>
      ) : (
        <div className={styles.divider} aria-hidden="true" />
      )}

      <div className={styles.body}>{children}</div>
    </section>
  );
}
