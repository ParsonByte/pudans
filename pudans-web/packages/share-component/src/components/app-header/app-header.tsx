"use client";

import { useEffect, useRef, useState, type CSSProperties, type FocusEvent } from "react";

import type { UserInfo } from "@pudans-dev/api-client";
import { theme } from "antd";

import { useLoginState } from "../../hooks/use-login-state";

import { Brand } from "./header-brand";
import { HamburgerMenu } from "./header-hamburger-menu";
import { HeaderActions } from "./header-actions";
import { HeaderMenuButton } from "./header-menu-button";
import { MegaMenu } from "./header-mega-menu";
import { DEFAULT_APP_HEADER_MENUS } from "./header-menus";
import { NavItem } from "./header-nav-item";
import { SearchBox } from "./header-search-box";
import type { AppHeaderProps, AppHeaderUser } from "./header-types";
import { WorkspaceButton } from "./header-workspace-button";

import "./app-header.modules.less";

function toHeaderUser(user: UserInfo): AppHeaderUser {
  return {
    avatarSrc: user.avatar,
    id: user.userId,
    name: user.fullName || user.nickName || user.username || user.userId || "",
  };
}

export function AppHeader({
  actions,
  brandName = "PUDANS",
  className,
  consoleHref = "/console",
  docsHref = "/docs",
  loginGateEnabled = true,
  loginHref = "/login",
  loginStateEnabled = true,
  logoSrc = "/ui-assets/pudans.svg",
  menus = DEFAULT_APP_HEADER_MENUS,
  onAccessKeyClick,
  onAccountCenterClick,
  onConsoleClick,
  onDocsClick,
  onLoginClick,
  onLogout,
  onSearch,
  onSecurityClick,
  searchDiscoveries,
  searchHistory,
  searchPlaceholder = "搜索",
  showSearch = true,
  style,
  user,
}: AppHeaderProps) {
  const rootRef = useRef<HTMLElement>(null);
  const { token } = theme.useToken();
  const loginState = useLoginState({ enabled: loginStateEnabled && !user });
  const [activeMenuKey, setActiveMenuKey] = useState<string | null>(null);
  const [hamburgerOpen, setHamburgerOpen] = useState(false);
  const [searchExpanded, setSearchExpanded] = useState(false);

  const activeMenu = menus.find((menu) => menu.key === activeMenuKey);
  const resolvedUser = user ?? (loginState.user ? toHeaderUser(loginState.user) : undefined);
  const userLoading = loginStateEnabled && !user && loginState.isLoading;
  const anonymous = !resolvedUser && (loginStateEnabled
    ? loginState.status === "anonymous" || loginState.status === "error"
    : true);
  const locked = loginGateEnabled && anonymous;
  const resolvedLogout =
    onLogout ?? (loginStateEnabled && !user ? loginState.logout : undefined);
  const rootStyle = {
    ...style,
    "--pudans-header-bg": token.colorBgContainer,
    "--pudans-header-brand": token.colorPrimary,
    "--pudans-header-brand-soft": token.colorPrimaryBg,
    "--pudans-header-divider": token.colorSplit,
    "--pudans-header-text": token.colorText,
    "--pudans-header-muted": token.colorTextSecondary,
    "--pudans-header-fill": token.colorFillTertiary,
    "--pudans-header-fill-hover": token.colorFillSecondary,
    "--pudans-header-border": token.colorBorderSecondary,
    "--pudans-header-on-brand": token.colorTextLightSolid,
    "--pudans-header-shadow": token.boxShadowSecondary,
  } as CSSProperties;

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setActiveMenuKey(null);
        setHamburgerOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setActiveMenuKey(null);
        setHamburgerOpen(false);
      }
    };

    const closeMenus = () => {
      setActiveMenuKey(null);
      setHamburgerOpen(false);
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    document.documentElement.addEventListener("mouseleave", closeMenus);
    window.addEventListener("blur", closeMenus);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      document.documentElement.removeEventListener("mouseleave", closeMenus);
      window.removeEventListener("blur", closeMenus);
    };
  }, []);

  useEffect(() => {
    if (!activeMenuKey) {
      return;
    }
    const handlePointerMove = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      if (target.closest(".pudans-app-header__nav")
          || target.closest(".pudans-app-header__mega")) {
        return;
      }
      setActiveMenuKey(null);
    };
    document.addEventListener("pointermove", handlePointerMove);
    return () => {
      document.removeEventListener("pointermove", handlePointerMove);
    };
  }, [activeMenuKey]);

  const handleBlur = (event: FocusEvent<HTMLElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setActiveMenuKey(null);
      setHamburgerOpen(false);
    }
  };

  const handleHamburgerSelect = (key: string) => {
    setHamburgerOpen(false);
    setActiveMenuKey(key);
  };

  const rootClassName = [
    "pudans-app-header",
    searchExpanded ? "pudans-app-header--search-expanded" : "",
    activeMenu ? "pudans-app-header--menu-open" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <header
      className={rootClassName}
      onBlur={handleBlur}
      onMouseLeave={() => setActiveMenuKey(null)}
      ref={rootRef}
      style={rootStyle}
    >
      <div className="pudans-app-header__inner">
        <div className="pudans-app-header__left">
          <HeaderMenuButton
            expanded={hamburgerOpen}
            onClick={() => {
              setHamburgerOpen((open) => !open);
              setActiveMenuKey(null);
            }}
          />
          <span aria-hidden="true" className="pudans-app-header__divider" />
          <Brand brandName={brandName} logoSrc={logoSrc} />
          <WorkspaceButton href={consoleHref} />
          <nav aria-label="主导航" className="pudans-app-header__nav">
            {menus.map((menu) => (
              <NavItem
                active={activeMenuKey === menu.key}
                key={menu.key}
                menu={menu}
                onClick={() => setActiveMenuKey(menu.key)}
                onMouseEnter={() => setActiveMenuKey(menu.key)}
              />
            ))}
          </nav>
        </div>
        <div className="pudans-app-header__right">
          {showSearch ? (
            <SearchBox
              discoveries={searchDiscoveries}
              history={searchHistory}
              locked={locked}
              loginHref={loginHref}
              onExpandedChange={setSearchExpanded}
              onSearch={onSearch}
              placeholder={searchPlaceholder}
            />
          ) : null}
          {actions ?? (
            <HeaderActions
              consoleHref={consoleHref}
              docsHref={docsHref}
              getPopupContainer={() => rootRef.current ?? document.body}
              loading={userLoading}
              loginHref={loginHref}
              onAccessKeyClick={onAccessKeyClick}
              onAccountCenterClick={onAccountCenterClick}
              onConsoleClick={onConsoleClick}
              onDocsClick={onDocsClick}
              onLoginClick={onLoginClick}
              onLogout={resolvedLogout}
              onSecurityClick={onSecurityClick}
              user={resolvedUser}
            />
          )}
        </div>
      </div>
      {activeMenu ? (
        <MegaMenu
          activeKey={activeMenu.key}
          locked={locked}
          loginHref={loginHref}
          menus={menus}
        />
      ) : null}
      {hamburgerOpen ? (
        <HamburgerMenu
          locked={locked}
          loginHref={loginHref}
          menus={menus}
          onSelect={handleHamburgerSelect}
        />
      ) : null}
    </header>
  );
}
