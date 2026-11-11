import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { DropdownMenu } from "../dropdown-menu";
import { Icon } from "../icon";
import styles from "./responsive-tabs.module.css";

export interface ResponsiveTabItem<T extends string = string> {
  key: T;
  label: ReactNode;
  icon?: ReactNode;
  badge?: ReactNode;
}

export interface ResponsiveTabsProps<T extends string = string> {
  items: readonly ResponsiveTabItem<T>[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel?: string;
  className?: string;
}

/**
 * Tabs that collapse the trailing items into an overflow menu when they no
 * longer fit. Migrated from the legacy Common/ResponsiveTabs and rebuilt on
 * tokenized styles.
 */
export function ResponsiveTabs<T extends string = string>({
  items,
  value,
  onChange,
  ariaLabel = "Tabs",
  className,
}: ResponsiveTabsProps<T>) {
  const listRef = useRef<HTMLDivElement>(null);
  const [overflowing, setOverflowing] = useState(false);

  useLayoutEffect(() => {
    const node = listRef.current;
    if (!node) {
      return;
    }
    const measure = (): void => {
      setOverflowing(node.scrollWidth > node.clientWidth + 1);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, [items.length]);

  const select = useCallback(
    (key: T) => {
      onChange(key);
    },
    [onChange],
  );

  const active = items.find((item) => item.key === value) ?? items[0];
  const visible = overflowing && active ? [active] : items;

  return (
    <div className={cn(styles.root, className)}>
      <div ref={listRef} className={styles.list} role="tablist" aria-label={ariaLabel}>
        {visible.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={item.key === value}
            className={cn(styles.tab, item.key === value && styles.selected)}
            onClick={() => select(item.key)}
          >
            {item.icon ? (
              <span className={styles.icon} aria-hidden="true">
                {item.icon}
              </span>
            ) : null}
            <span className={styles.label}>{item.label}</span>
            {item.badge ? <span className={styles.badge}>{item.badge}</span> : null}
          </button>
        ))}
        {overflowing ? (
          <DropdownMenu
            trigger={<Icon name="cr:MoreHorizontal" size={18} />}
            triggerLabel="Show more tabs"
            showChevron={false}
            items={items.map((item) => ({
              key: item.key,
              label: item.label,
              icon: item.icon,
              onSelect: () => select(item.key),
            }))}
          />
        ) : null}
      </div>
    </div>
  );
}
