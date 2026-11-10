import {
  forwardRef,
  useEffect,
  useState,
  type ComponentType,
  type CSSProperties,
  type ElementType,
  type HTMLAttributes,
  type MouseEvent,
  type ReactNode,
} from "react";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import { cn } from "../../utils/cn";
import { useHotkeys } from "react-hotkeys-hook";
import { Avatar } from "../../components/avatar";
import { DropdownMenu, type DropdownMenuItem } from "../../components/dropdown-menu";
import { Icon } from "../../components/icon";
import { SideNavItem } from "./side-nav-item";
import styles from "./app-shell.module.css";

export interface BreadcrumbItem {
  label: ReactNode;
  href?: string;
  onClick?: () => void;
}

export interface AppShellUser {
  name: string;
  description?: string;
  avatarSrc?: string;
  avatarIcon?: ReactNode;
}

/** One navigation entry. The shell never knows which router the app uses. */
export interface AppShellMenuItem {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  /** When present the item renders through `linkComponent` (or an <a>). */
  href?: string;
  active?: boolean;
  trailing?: ReactNode;
  title?: string;
  /** Used when the item has no href (renders a button). */
  onSelect?: () => void;
}

/** Contract for the injected router link (react-router `Link`, Next `Link`, …). */
export interface AppShellMenuLinkProps {
  href: string;
  className?: string;
  children?: ReactNode;
  title?: string;
  "aria-current"?: "page";
  onClick?: (event: MouseEvent<HTMLElement>) => void;
}

export type AppShellLinkComponent = ComponentType<AppShellMenuLinkProps>;

export interface AppShellProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  children: ReactNode;
  /** Navigation data. Provide hrefs with linkComponent to keep client routing. */
  menuItems?: readonly AppShellMenuItem[];
  /** Escape hatch for custom sidebar content rendered below the menu. */
  sidebar?: ReactNode;
  sidebarHeader?: ReactNode;
  sidebarFooter?: ReactNode;
  /** Accessible name for the navigation landmark. */
  sidebarLabel?: string;
  /** Router link adapter injected by the host application. */
  linkComponent?: AppShellLinkComponent;
  header?: ReactNode;
  breadcrumbs?: readonly BreadcrumbItem[];
  user?: AppShellUser;
  userMenuItems?: readonly DropdownMenuItem[];
  headerActions?: ReactNode;
  footer?: ReactNode;
  collapsed?: boolean;
  defaultCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  /** Overrides the library token --pd-shell-drawer-width (240px). */
  sidebarWidth?: number;
  logo?: ReactNode;
  appName?: ReactNode;
  /** Click handler for the drawer brand block (usually "go home"). */
  onLogoClick?: () => void;
  enableDnd?: boolean;
  enableHotkeys?: boolean;
  onSearchShortcut?: () => void;
}

const MOBILE_QUERY = "(max-width: 900px)";

function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(
    () => typeof window !== "undefined" && window.matchMedia(query).matches,
  );

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const mql = window.matchMedia(query);
    const update = (): void => setMatches(mql.matches);
    update();
    mql.addEventListener("change", update);
    return () => mql.removeEventListener("change", update);
  }, [query]);

  return matches;
}

function Breadcrumbs({ items }: { items: readonly BreadcrumbItem[] }) {
  return (
    <nav className={styles.breadcrumbs} aria-label="Breadcrumb">
      {items.map((item, index) => {
        const isLast = index === items.length - 1;
        const content = item.href ? (
          <a
            className={styles.crumbLink}
            href={item.href}
            aria-current={isLast ? "page" : undefined}
          >
            {item.label}
          </a>
        ) : item.onClick ? (
          <button
            type="button"
            className={styles.crumbButton}
            onClick={item.onClick}
            aria-current={isLast ? "page" : undefined}
          >
            {item.label}
          </button>
        ) : (
          <span className={styles.crumbText} aria-current={isLast ? "page" : undefined}>
            {item.label}
          </span>
        );

        return (
          <span key={index} className={styles.crumb}>
            {content}
            {!isLast ? (
              <span className={styles.crumbSeparator} aria-hidden="true">
                /
              </span>
            ) : null}
          </span>
        );
      })}
    </nav>
  );
}

/**
 * Application shell: a persistent drawer, a transparent app bar and a content
 * canvas. Router- and product-agnostic — navigation comes from `menuItems`,
 * links are rendered by the injected `linkComponent`, and the header/user
 * area is composed by the host application.
 *
 * Below 900px the drawer becomes an off-canvas panel with a scrim, opened from
 * the app-bar menu button.
 */
export const AppShell = forwardRef<HTMLDivElement, AppShellProps>(function AppShell(
  {
    children,
    menuItems,
    sidebar,
    sidebarHeader,
    sidebarFooter,
    sidebarLabel = "Sidebar",
    linkComponent,
    header,
    breadcrumbs,
    user,
    userMenuItems,
    headerActions,
    footer,
    collapsed,
    defaultCollapsed = false,
    onCollapsedChange,
    sidebarWidth,
    logo,
    appName,
    onLogoClick,
    enableDnd = true,
    enableHotkeys = true,
    onSearchShortcut,
    className,
    style,
    ...props
  },
  ref,
) {
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed);
  const isCollapsed = collapsed ?? internalCollapsed;
  const isMobile = useMediaQuery(MOBILE_QUERY);
  const [mobileOpen, setMobileOpen] = useState(false);

  const hasDrawer = Boolean(menuItems?.length || sidebar || logo || appName);

  useEffect(() => {
    if (!isMobile) {
      setMobileOpen(false);
    }
  }, [isMobile]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        setMobileOpen(false);
      }
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [mobileOpen]);

  const toggleSidebar = (): void => {
    if (isMobile) {
      setMobileOpen((open) => !open);
      return;
    }
    const next = !isCollapsed;
    if (collapsed === undefined) {
      setInternalCollapsed(next);
    }
    onCollapsedChange?.(next);
  };

  const closeMobileDrawer = (): void => {
    if (isMobile) {
      setMobileOpen(false);
    }
  };

  useHotkeys(
    "mod+b",
    (event) => {
      event.preventDefault();
      if (isMobile) {
        setMobileOpen((open) => !open);
        return;
      }
      toggleSidebar();
    },
    { enabled: enableHotkeys && hasDrawer, preventDefault: true },
    [enableHotkeys, hasDrawer, isCollapsed, isMobile],
  );

  useHotkeys(
    "mod+k",
    (event) => {
      event.preventDefault();
      onSearchShortcut?.();
    },
    { enabled: enableHotkeys && Boolean(onSearchShortcut), preventDefault: true },
    [enableHotkeys, onSearchShortcut],
  );

  const MenuLink = (linkComponent ?? "a") as ElementType;
  const brandLabel = typeof appName === "string" && appName.length > 0 ? appName : "Home";

  const userTrigger = user ? (
    <span className={styles.user}>
      <Avatar
        name={user.name}
        src={user.avatarSrc}
        icon={user.avatarIcon}
        size="md"
        alt={user.name}
      />
      <span className={styles.userText}>
        <span className={styles.userName}>{user.name}</span>
        {user.description ? (
          <span className={styles.userDescription}>{user.description}</span>
        ) : null}
      </span>
    </span>
  ) : null;

  const showMenuButton = hasDrawer && (isMobile || isCollapsed);

  const defaultTopAppBar = (
    <>
      <div className={styles.topAppBarStart}>
        {showMenuButton ? (
          <button
            type="button"
            className={styles.iconButton}
            onClick={toggleSidebar}
            aria-label={
              isMobile ? (mobileOpen ? "Close sidebar" : "Open sidebar") : "Expand sidebar"
            }
            aria-expanded={isMobile ? mobileOpen : false}
            aria-controls="pd-app-drawer"
            aria-keyshortcuts="Meta+B Control+B"
          >
            <Icon name="mdi:menu" size={22} />
          </button>
        ) : null}
        {breadcrumbs?.length ? <Breadcrumbs items={breadcrumbs} /> : null}
      </div>

      <div className={styles.topAppBarEnd}>
        {headerActions}
        {user && userMenuItems?.length ? (
          <DropdownMenu
            trigger={userTrigger}
            items={userMenuItems}
            triggerLabel={`Open user menu for ${user.name}`}
            triggerClassName={styles.userMenuTrigger}
            showChevron={false}
          />
        ) : (
          userTrigger
        )}
      </div>
    </>
  );

  const shellStyle = {
    ...(sidebarWidth !== undefined ? { "--pd-shell-drawer-width": `${sidebarWidth}px` } : {}),
    ...style,
  } as CSSProperties;

  const shell = (
    <div ref={ref} className={cn(styles.navBarFrame, className)} style={shellStyle} {...props}>
      <a className="pd-skip-link" href="#pd-main">
        Skip to content
      </a>

      {isMobile && mobileOpen ? (
        <button
          type="button"
          className={styles.overlay}
          aria-label="Close sidebar"
          onClick={() => setMobileOpen(false)}
        />
      ) : null}

      {hasDrawer ? (
        <aside
          id="pd-app-drawer"
          className={cn(
            styles.appDrawer,
            !isMobile && isCollapsed && styles.appDrawerCollapsed,
            isMobile && mobileOpen && styles.appDrawerMobileOpen,
          )}
          aria-label={sidebarLabel}
          aria-hidden={isMobile && !mobileOpen}
        >
          <div className={styles.drawerHeader}>
            <button
              type="button"
              className={styles.drawerBrand}
              onClick={() => {
                onLogoClick?.();
                closeMobileDrawer();
              }}
              aria-label={brandLabel}
            >
              {logo ? (
                <span className={styles.brandMark} aria-hidden="true">
                  {logo}
                </span>
              ) : null}
              {appName ? <span className={styles.brandName}>{appName}</span> : null}
            </button>

            {!isMobile && !isCollapsed ? (
              <button
                type="button"
                className={styles.drawerCollapse}
                onClick={toggleSidebar}
                aria-label="Collapse sidebar"
                aria-expanded
                aria-keyshortcuts="Meta+B Control+B"
              >
                <Icon name="mdi:chevron-left" size={20} />
              </button>
            ) : null}
          </div>

          <div className={cn(styles.drawerNav, "pd-scrollbar")} onClick={closeMobileDrawer}>
            {sidebarHeader ? <div className={styles.drawerSection}>{sidebarHeader}</div> : null}

            {menuItems?.length ? (
              <nav className={styles.drawerMenu} aria-label={sidebarLabel}>
                {menuItems.map((item) =>
                  item.href ? (
                    <SideNavItem
                      key={item.key}
                      component={MenuLink}
                      href={item.href}
                      icon={item.icon}
                      label={item.label}
                      active={item.active}
                      trailing={item.trailing}
                      title={item.title}
                      onClick={item.onSelect}
                    />
                  ) : (
                    <SideNavItem
                      key={item.key}
                      icon={item.icon}
                      label={item.label}
                      active={item.active}
                      trailing={item.trailing}
                      title={item.title}
                      onClick={item.onSelect}
                    />
                  ),
                )}
              </nav>
            ) : null}

            {sidebar}

            {sidebarFooter ? <div className={styles.drawerFooter}>{sidebarFooter}</div> : null}
          </div>
        </aside>
      ) : null}

      <div className={styles.appMain}>
        <header className={styles.topAppBar}>{header ?? defaultTopAppBar}</header>

        <main id="pd-main" className={styles.appContent} tabIndex={-1}>
          {children}
        </main>

        {footer ? <footer className={styles.appFooter}>{footer}</footer> : null}
      </div>
    </div>
  );

  return enableDnd ? <DndProvider backend={HTML5Backend}>{shell}</DndProvider> : shell;
});

AppShell.displayName = "AppShell";
