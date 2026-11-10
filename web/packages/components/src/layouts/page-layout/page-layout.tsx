import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./page-layout.module.css";

export interface PageLayoutProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "title" | "children"
> {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * Page-level layout for standalone screens: a local header with title and
 * actions, a scrollable body, and an optional footer. It intentionally has no
 * sidebar or global top navigation.
 */
export const PageLayout = forwardRef<HTMLDivElement, PageLayoutProps>(function PageLayout(
  { title, description, actions, header, footer, children, className, ...props },
  ref,
) {
  const defaultHeader = (
    <div className={styles.headerInner}>
      <div className={styles.headingGroup}>
        {title ? <h1 className={styles.title}>{title}</h1> : null}
        {description ? <p className={styles.description}>{description}</p> : null}
      </div>
      {actions ? <div className={styles.actions}>{actions}</div> : null}
    </div>
  );

  return (
    <div ref={ref} className={cn(styles.root, className)} {...props}>
      <header className={styles.header}>{header ?? defaultHeader}</header>
      <main className={cn(styles.body, "pd-scrollbar")}>{children}</main>
      {footer ? <footer className={styles.footer}>{footer}</footer> : null}
    </div>
  );
});

PageLayout.displayName = "PageLayout";
