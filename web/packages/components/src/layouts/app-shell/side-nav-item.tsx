import {
  createElement,
  type ComponentPropsWithoutRef,
  type ElementType,
  type ReactNode,
} from "react";
import { cn } from "../../utils/cn";
import styles from "./side-nav-item.module.css";

export type SideNavItemProps<C extends ElementType = "button"> = {
  /** Leading icon (20px recommended). */
  icon?: ReactNode;
  label: ReactNode;
  active?: boolean;
  /** Hide the label and center the icon; used by the collapsed sidebar. */
  collapsed?: boolean;
  trailing?: ReactNode;
  /** Element used for the item root. Defaults to a button; pass a router Link to keep client navigation. */
  component?: C;
} & Omit<ComponentPropsWithoutRef<C>, "children">;

interface SideNavItemRenderProps {
  icon?: ReactNode;
  label: ReactNode;
  active?: boolean;
  collapsed?: boolean;
  trailing?: ReactNode;
  component?: ElementType;
  className?: string;
  title?: string;
  [prop: string]: unknown;
}

/**
 * Cloudreve-style sidebar entry: a transparent 40px pill that gains a faint
 * hover veil and a soft brand tint when active. State colors live in
 * `--pd-shell-item-*` variables owned by AppShell, with neutral fallbacks so
 * the item also works outside a shell.
 */
export function SideNavItem<C extends ElementType = "button">(props: SideNavItemProps<C>) {
  const {
    icon,
    label,
    active = false,
    collapsed = false,
    trailing,
    component,
    className,
    title,
    ...rest
  } = props as SideNavItemRenderProps;

  const Component = (component ?? "button") as ElementType;

  return createElement(
    Component,
    {
      ...rest,
      type: Component === "button" ? "button" : undefined,
      className: cn(styles.item, active && styles.active, collapsed && styles.collapsed, className),
      "aria-current": active ? "page" : undefined,
      title: title ?? (collapsed && typeof label === "string" ? label : undefined),
    },
    <>
      {icon ? (
        <span className={styles.icon} aria-hidden="true">
          {icon}
        </span>
      ) : null}
      <span className={styles.label}>{label}</span>
      {trailing ? <span className={styles.trailing}>{trailing}</span> : null}
    </>,
  );
}

SideNavItem.displayName = "SideNavItem";
