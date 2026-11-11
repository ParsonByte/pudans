import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { NuqsAdapter } from "nuqs/adapters/react-router/v6";
import {
  AppShell,
  Icon,
  LanguageSwitcher,
  SideNavItem,
  ThemeSwitch,
  type AppShellLinkComponent,
  type AppShellMenuItem,
  type AppShellUser,
  type BreadcrumbItem,
  type DropdownMenuItem,
} from "@pudans/components";
import { useAppDispatch } from "@web/store/hooks";
import { setLocale } from "@web/store/app-slice";
import { useAuth, usePermissions } from "@web/providers/auth-provider";
import styles from "./console-layout.module.css";

const DASHBOARD_NAV = {
  to: "/",
  labelKey: "navDashboard",
  icon: "mdi:view-dashboard-outline",
  permission: "dashboard:view",
} as const;

const NAV_ITEMS = [
  {
    to: "/settings",
    labelKey: "navSettings",
    icon: "mdi:cog-outline",
    permission: "settings:manage",
  },
  { to: "/users", labelKey: "navUsers", icon: "mdi:account-outline", permission: "users:manage" },
  {
    to: "/roles",
    labelKey: "navRoles",
    icon: "mdi:shield-account-outline",
    permission: "roles:manage",
  },
  {
    to: "/usergroups",
    labelKey: "navUserGroups",
    icon: "mdi:account-group-outline",
    permission: "users:manage",
  },
  {
    to: "/plugins",
    labelKey: "navPlugins",
    icon: "mdi:puzzle-outline",
    permission: "plugins:manage",
  },
  {
    to: "/integrations",
    labelKey: "navIntegrations",
    icon: "mdi:connection",
    permission: "webhooks:manage",
  },
  {
    to: "/audit",
    labelKey: "navAudit",
    icon: "mdi:file-document-outline",
    permission: "audit:view",
  },
  { to: "/risk", labelKey: "navRisk", icon: "mdi:shield-alert-outline", permission: "risk:manage" },
] as const;

const ROUTE_LABELS: Record<string, string> = {
  "/": "navDashboard",
  "/settings": "navSettings",
  "/users": "navUsers",
  "/roles": "navRoles",
  "/usergroups": "navUserGroups",
  "/plugins": "navPlugins",
  "/integrations": "navIntegrations",
  "/audit": "navAudit",
  "/risk": "navRisk",
};

/** Keeps client-side routing without letting the shell depend on react-router. */
const ConsoleMenuLink: AppShellLinkComponent = ({ href, ...rest }) => (
  <NavLink to={href} end={href === "/"} {...rest} />
);

export function ConsoleLayout() {
  const { t, i18n } = useTranslation("console");
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [collapsed, setCollapsed] = useState(false);
  const { user: currentUser } = useAuth();
  const { hasPermission } = usePermissions();

  const user: AppShellUser = {
    name: currentUser?.displayName || t("administrator"),
    description: currentUser?.email || currentUser?.name || t("brandSub"),
  };

  const labelKey = ROUTE_LABELS[location.pathname] ?? "navDashboard";
  const breadcrumbs: BreadcrumbItem[] = [
    {
      label: t("breadcrumbRoot"),
      onClick: () => navigate("/"),
    },
    { label: t(labelKey) },
  ];

  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";

  const changeLanguage = async (locale: string): Promise<void> => {
    dispatch(setLocale(locale));
    await i18n.changeLanguage(locale);
  };

  const logout = async (): Promise<void> => {
    try {
      await fetch("/logout", {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "X-Requested-With": "XMLHttpRequest",
        },
      });
    } finally {
      window.location.assign("/auth/");
    }
  };

  const isActive = (to: string): boolean =>
    to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);

  const visibleNavItems = NAV_ITEMS.filter((item) => hasPermission(item.permission));

  const showDashboardNav = hasPermission(DASHBOARD_NAV.permission);

  const sidebarHeader = showDashboardNav ? (
    <SideNavItem
      component={ConsoleMenuLink}
      href={DASHBOARD_NAV.to}
      icon={<Icon name={DASHBOARD_NAV.icon} size={20} />}
      label={t(DASHBOARD_NAV.labelKey)}
      active={isActive(DASHBOARD_NAV.to)}
    />
  ) : null;

  const menuItems: AppShellMenuItem[] = visibleNavItems.map((item) => ({
    key: item.to,
    href: item.to,
    label: t(item.labelKey),
    icon: <Icon name={item.icon} size={20} />,
    active: isActive(item.to),
  }));

  const userMenuItems: readonly DropdownMenuItem[] = [
    {
      key: "profile",
      label: t("profile"),
      icon: <Icon name="mdi:account-circle-outline" size={16} />,
      onSelect: () => {
        window.location.assign("/uc/");
      },
    },
    { key: "divider", divider: true },
    {
      key: "logout",
      label: t("logout"),
      icon: <Icon name="mdi:logout" size={16} />,
      danger: true,
      onSelect: () => {
        void logout();
      },
    },
  ];

  return (
    <NuqsAdapter>
      <AppShell
        appName="PUDANS"
        logo={<span className={styles.logoGlyph}>P</span>}
        onLogoClick={() => navigate("/")}
        menuItems={menuItems}
        linkComponent={ConsoleMenuLink}
        sidebarLabel={t("breadcrumbRoot")}
        sidebarHeader={sidebarHeader}
        sidebarFooter={
          <SideNavItem
            component="a"
            href="/uc/"
            icon={<Icon name="mdi:home-outline" size={20} />}
            label={t("backHome")}
          />
        }
        breadcrumbs={breadcrumbs}
        user={user}
        userMenuItems={userMenuItems}
        headerActions={
          <div className={styles.headerActions}>
            <ThemeSwitch size="sm" aria-label={t("theme")} />
            <LanguageSwitcher
              compact
              ariaLabel={t("language")}
              languages={[
                { code: "zh-CN", label: t("languageZh") },
                { code: "en-US", label: t("languageEn") },
              ]}
              value={currentLanguage}
              onChange={(locale) => {
                void changeLanguage(locale);
              }}
            />
          </div>
        }
        collapsed={collapsed}
        onCollapsedChange={setCollapsed}
      >
        <Outlet />
      </AppShell>
    </NuqsAdapter>
  );
}
