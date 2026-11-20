import type { ElementType } from "react";

type User = {
  name: string;
  email: string;
  avatar: string;
};

type Workspace = {
  id: string;
  name: string;
  logo: ElementType;
  logoImage?: boolean;
  plan: string;
  homeUrl: string;
  paths: string[];
};

type BaseNavItem = {
  title: string;
  badge?: string;
  icon?: ElementType;
};

type NavUrl = string;

type NavLink = BaseNavItem & {
  url: NavUrl;
  items?: never;
};

type NavCollapsible = BaseNavItem & {
  items: (BaseNavItem & { url: NavUrl })[];
  url?: never;
};

type NavItem = NavCollapsible | NavLink;

type NavGroup = {
  title: string;
  items: NavItem[];
};

type SidebarData = {
  user: User;
  workspaces: Workspace[];
  activeWorkspaceId?: string;
  navGroups: NavGroup[];
};

export type {
  BaseNavItem,
  NavCollapsible,
  NavGroup,
  NavItem,
  NavLink,
  NavUrl,
  SidebarData,
  User,
  Workspace,
};
