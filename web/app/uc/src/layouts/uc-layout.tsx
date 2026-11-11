import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  AppShell,
  Icon,
  LanguageSwitcher,
  ThemeSwitch,
  type AppShellLinkComponent,
  type AppShellMenuItem,
  type AppShellUser,
  type BreadcrumbItem,
  type DropdownMenuItem,
} from "@pudans/components";
import { useAppDispatch } from "@web/store/hooks";
import { setLocale } from "@web/store/app-slice";
import { useAuth } from "@web/providers/auth-provider";
import styles from "./uc-layout.module.css";

const NAV_ITEMS = [
  { to: "/", labelKey: "navProfile", icon: "mdi:account-circle-outline" },
  { to: "/security", labelKey: "navSecurity", icon: "mdi:shield-lock-outline" },
  { to: "/authorizations", labelKey: "navAuthorizations", icon: "mdi:key-variant" },
] as const;

const ROUTE_LABELS: Record<string, string> = {
  "/": "navProfile",
  "/security": "navSecurity",
  "/authorizations": "navAuthorizations",
};

const UcMenuLink: AppShellLinkComponent = ({ href, ...rest }) => (
  <NavLink to={href} end={href === "/"} {...rest} />
);

export function UcLayout() {
  const { t, i18n } = useTranslation("uc");
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [collapsed, setCollapsed] = useState(false);
  const { user: currentUser } = useAuth();

  const user: AppShellUser = {
    name: currentUser?.displayName || t("account"),
    description: currentUser?.email || currentUser?.name || t("brandSub"),
  };

  const labelKey = ROUTE_LABELS[location.pathname] ?? "navProfile";
  const breadcrumbs: BreadcrumbItem[] = [
    { label: t("breadcrumbRoot"), onClick: () => navigate("/") },
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

  const menuItems: AppShellMenuItem[] = NAV_ITEMS.map((item) => ({
    key: item.to,
    href: item.to,
    label: t(item.labelKey),
    icon: <Icon name={item.icon} size={20} />,
    active: isActive(item.to),
  }));

  const userMenuItems: readonly DropdownMenuItem[] = [
    {
      key: "profile",
      label: t("navProfile"),
      icon: <Icon name="mdi:account-circle-outline" size={16} />,
      onSelect: () => {
        navigate("/");
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
    <AppShell
      appName="PUDANS"
      logo={<span className={styles.logoGlyph}>P</span>}
      onLogoClick={() => navigate("/")}
      menuItems={menuItems}
      linkComponent={UcMenuLink}
      sidebarLabel={t("breadcrumbRoot")}
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
  );
}
