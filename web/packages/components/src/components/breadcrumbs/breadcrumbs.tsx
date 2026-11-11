import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import { DropdownMenu } from "../dropdown-menu";
import { Icon } from "../icon";
import styles from "./breadcrumbs.module.css";

export interface BreadcrumbEntry {
  key?: string;
  label: ReactNode;
  icon?: ReactNode | string;
  href?: string;
  onClick?: () => void;
}

export interface BreadcrumbsProps {
  items: readonly BreadcrumbEntry[];
  /** Collapse the middle into an overflow menu above this length. */
  maxItems?: number;
  separator?: ReactNode;
  ariaLabel?: string;
  className?: string;
}

function itemKey(item: BreadcrumbEntry, index: number): string {
  return item.key ?? (typeof item.label === "string" ? item.label : "crumb-" + index);
}

function renderIcon(icon: ReactNode | string | undefined) {
  if (!icon) {
    return null;
  }
  return typeof icon === "string" ? (
    <Icon name={icon} size={16} className={styles.icon} />
  ) : (
    <span className={styles.icon}>{icon}</span>
  );
}

function Crumb({ item, current }: { item: BreadcrumbEntry; current: boolean }) {
  const content = (
    <>
      {renderIcon(item.icon)}
      <span className={styles.label}>{item.label}</span>
    </>
  );
  if (item.href) {
    return (
      <a
        href={item.href}
        className={cn(styles.crumb, current && styles.current)}
        aria-current={current ? "page" : undefined}
      >
        {content}
      </a>
    );
  }
  if (item.onClick && !current) {
    return (
      <button
        type="button"
        className={cn(styles.crumb, current && styles.current)}
        onClick={item.onClick}
      >
        {content}
      </button>
    );
  }
  return (
    <span
      className={cn(styles.crumb, styles.static, current && styles.current)}
      aria-current={current ? "page" : undefined}
    >
      {content}
    </span>
  );
}

/**
 * Breadcrumb navigation migrated from the legacy FileManager TopBar trio.
 * Supports leading icons, link/button/static items and middle collapsing.
 */
export function Breadcrumbs({
  items,
  maxItems,
  separator,
  ariaLabel = "Breadcrumb",
  className,
}: BreadcrumbsProps) {
  const collapse = maxItems !== undefined && items.length > maxItems && maxItems >= 3;
  const keepTail = collapse ? maxItems - 2 : items.length;
  const visibleHead = collapse ? [items[0]] : items.slice(0, items.length - keepTail);
  const hidden = collapse ? items.slice(1, items.length - keepTail) : [];
  const visibleTail = items.slice(items.length - keepTail);
  const visible = [...visibleHead, ...visibleTail];
  const sep = separator ?? (
    <Icon name="cr:ChevronRight" size={14} className={styles.separatorIcon} />
  );

  const renderList = () =>
    visible.map((item, index) => {
      const globalIndex = collapse
        ? index === 0
          ? 0
          : items.length - keepTail + index - 1
        : index;
      return (
        <li key={itemKey(item, globalIndex)} className={styles.item}>
          <Crumb item={item} current={globalIndex === items.length - 1} />
          {globalIndex === items.length - 1 ? null : (
            <span className={styles.separator} aria-hidden="true">
              {sep}
            </span>
          )}
        </li>
      );
    });

  return (
    <nav aria-label={ariaLabel} className={cn(styles.root, className)}>
      <ol className={styles.list}>
        {collapse ? (
          <>
            {renderList().slice(0, 1)}
            <li className={styles.item}>
              <DropdownMenu
                trigger={<Icon name="cr:MoreHorizontal" size={16} />}
                triggerLabel="Show hidden path segments"
                showChevron={false}
                items={hidden.map((item, index) => ({
                  key: itemKey(item, index),
                  label: item.label,
                  icon: renderIcon(item.icon),
                  onSelect: item.onClick,
                }))}
              />
            </li>
            {renderList().slice(1)}
          </>
        ) : (
          renderList()
        )}
      </ol>
    </nav>
  );
}
