import { useId, useState, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./accordion.module.css";

export interface AccordionProps {
  title: ReactNode;
  children: ReactNode;
  defaultExpanded?: boolean;
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  variant?: "card" | "ghost";
  icon?: ReactNode;
  disabled?: boolean;
  className?: string;
}

/**
 * Collapsible section migrated from the legacy DialogAccordion. Uses the grid
 * 0fr/1fr technique so it animates height with token durations while staying
 * keyboard accessible.
 */
export function Accordion({
  title,
  children,
  defaultExpanded = false,
  expanded: controlledExpanded,
  onExpandedChange,
  variant = "card",
  icon,
  disabled = false,
  className,
}: AccordionProps) {
  const [uncontrolled, setUncontrolled] = useState(defaultExpanded);
  const expanded = controlledExpanded ?? uncontrolled;
  const contentId = useId();
  const headerId = useId();

  const toggle = (): void => {
    if (disabled) {
      return;
    }
    const next = !expanded;
    if (controlledExpanded === undefined) {
      setUncontrolled(next);
    }
    onExpandedChange?.(next);
  };

  return (
    <section
      className={cn(styles.root, styles[variant], className)}
      data-expanded={expanded || undefined}
    >
      <h3 className={styles.heading}>
        <button
          id={headerId}
          type="button"
          className={styles.trigger}
          aria-expanded={expanded}
          aria-controls={contentId}
          onClick={toggle}
          disabled={disabled}
        >
          {icon ? (
            <span className={styles.titleIcon} aria-hidden="true">
              {icon}
            </span>
          ) : null}
          <span className={styles.title}>{title}</span>
          <Icon
            name="cr:CaretDown"
            size={18}
            className={cn(styles.chevron, expanded && styles.chevronOpen)}
          />
        </button>
      </h3>
      <div
        id={contentId}
        role="region"
        aria-labelledby={headerId}
        className={styles.region}
        aria-hidden={!expanded}
      >
        <div className={styles.inner}>
          <div className={styles.content}>{children}</div>
        </div>
      </div>
    </section>
  );
}

/** Semantic alias kept for migration parity with the legacy DialogAccordion. */
export const DialogAccordion = Accordion;
