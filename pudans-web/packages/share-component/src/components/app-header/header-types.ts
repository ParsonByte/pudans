import type { CSSProperties, ReactNode } from "react";

export type AppHeaderUser = {
  name: string;
  avatarSrc?: string;
  href?: string;
  id?: string;
};

export type AppHeaderMenuItem = {
  key: string;
  label: ReactNode;
  description?: ReactNode;
  href?: string;
  icon?: ReactNode;
};

export type AppHeaderMenuGroup = {
  key: string;
  title?: ReactNode;
  items: AppHeaderMenuItem[];
};

export type AppHeaderMenu = {
  key: string;
  label: ReactNode;
  description?: ReactNode;
  href?: string;
  groups?: AppHeaderMenuGroup[];
};

export type AppHeaderProps = {
  actions?: ReactNode;
  brandName?: string;
  className?: string;
  consoleHref?: string;
  docsHref?: string;
  loginGateEnabled?: boolean;
  loginHref?: string;
  loginStateEnabled?: boolean;
  logoSrc?: string;
  menus?: AppHeaderMenu[];
  onAccessKeyClick?: () => void;
  onAccountCenterClick?: () => void;
  onConsoleClick?: () => void;
  onDocsClick?: () => void;
  onLoginClick?: () => void;
  onLogout?: () => void;
  onSearch?: (keyword: string) => void;
  onSecurityClick?: () => void;
  searchDiscoveries?: string[];
  searchHistory?: string[];
  searchPlaceholder?: string;
  showSearch?: boolean;
  style?: CSSProperties;
  user?: AppHeaderUser;
};
