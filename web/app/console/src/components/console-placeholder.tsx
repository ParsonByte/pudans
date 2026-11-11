import { useTranslation } from "react-i18next";
import { Icon } from "@pudans/components";
import { PageContainer } from "@pudans/layouts";
import styles from "./console-placeholder.module.css";

export interface ConsoleEmptyStateProps {
  icon: string;
  title: string;
  description: string;
}

export function ConsoleEmptyState({ icon, title, description }: ConsoleEmptyStateProps) {
  return (
    <div className={styles.emptyCard}>
      <span className={styles.icon} aria-hidden="true">
        <Icon name={icon} size={34} />
      </span>
      <h2 className={styles.emptyTitle}>{title}</h2>
      <p className={styles.emptyText}>{description}</p>
    </div>
  );
}

export interface ConsolePlaceholderProps {
  titleKey: string;
}

/** Placeholder page: only the page title, matching the sidebar entry. */
export function ConsolePlaceholder({ titleKey }: ConsolePlaceholderProps) {
  const { t } = useTranslation("console");

  return <PageContainer title={t(titleKey)} />;
}
