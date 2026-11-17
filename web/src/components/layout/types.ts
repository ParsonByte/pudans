import type { ElementType } from "react";

import type { LinkProps } from "@tanstack/react-router";

type User = {
  name: string;
  email: string;
  avatar: string;
};

type Workspace = {
  name: string;
  logo: ElementType;
  plan: string;
};

type BaseNavItem = {
  title: string;
  badge?: string;
  icon?: ElementType;
};

type NavUrl = LinkProps["to"] | (string & {});

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
