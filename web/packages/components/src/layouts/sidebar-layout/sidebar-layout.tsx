import {
  forwardRef,
  useState,
  type CSSProperties,
  type HTMLAttributes,
  type ReactNode,
} from "react";
import { cn } from "../../utils/cn";
import { AnimateHeight } from "../../components/animate-height";
import { Icon } from "../../components/icon";
import styles from "./sidebar-layout.module.css";

export interface SidebarLayoutProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  children: ReactNode;
  sidebar: ReactNode;
  sidebarHeader?: ReactNode;
  sidebarFooter?: ReactNode;
  collapsed?: boolean;
  defaultCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  sidebarWidth?: number;
  collapsedWidth?: number;
  sidebarLabel?: string;
}

/**
 * Lightweight module/plugin shell: collapsible navigation on the left and a
 * pure content surface on the right. No global header or top navigation.
 */
export const SidebarLayout = forwardRef<HTMLDivElement, SidebarLayoutProps>(function SidebarLayout(
  {
    children,
    sidebar,
    sidebarHeader,
    sidebarFooter,
    collapsed,
    defaultCollapsed = false,
    onCollapsedChange,
    sidebarWidth = 248,
    collapsedWidth = 72,
    sidebarLabel = "Sidebar",
    className,
    style,
    ...props
  },
  ref,
) {
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed);
  const isCollapsed = collapsed ?? internalCollapsed;

  const toggle = (): void => {
    const next = !isCollapsed;
    if (collapsed === undefined) {
      setInternalCollapsed(next);
    }
    onCollapsedChange?.(next);
  };

  const layoutStyle = {
    "--pd-sidebar-width": `${sidebarWidth}px`,
    "--pd-sidebar-collapsed-width": `${collapsedWidth}px`,
    ...style,
  } as CSSProperties;

  return (
    <div ref={ref} className={cn(styles.root, className)} style={layoutStyle} {...props}>
      <aside
        className={cn(styles.sidebar, isCollapsed && styles.collapsed)}
        aria-label={sidebarLabel}
      >
        <div className={styles.sidebarHeader}>
          {sidebarHeader ? (
            <AnimateHeight
              open={!isCollapsed}
              duration={200}
              animateOpacity
              className={styles.headerContentWrap}
            >
              <div className={styles.headerContent}>{sidebarHeader}</div>
            </AnimateHeight>
          ) : null}
          <button
            type="button"
            className={styles.toggle}
            onClick={toggle}
            aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            aria-expanded={!isCollapsed}
          >
            <Icon
              name={isCollapsed ? "mdi:view-sequential-outline" : "mdi:view-compact-outline"}
              size={20}
            />
          </button>
        </div>
        <div className={cn(styles.nav, "pd-scrollbar")}>{sidebar}</div>
        {sidebarFooter ? <div className={styles.sidebarFooter}>{sidebarFooter}</div> : null}
      </aside>
      <main className={styles.content}>{children}</main>
    </div>
  );
});

SidebarLayout.displayName = "SidebarLayout";
