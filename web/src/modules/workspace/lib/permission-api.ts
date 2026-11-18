import { axiosInstance } from "@pudans-dev/api-client";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

type PermissionRule = {
  apiGroups?: string[];
  resources?: string[];
  resourceNames?: string[];
  nonResourceURLs?: string[];
  verbs?: string[];
};

type CatalogLevel = {
  template: string;
  permission?: string;
  capability?: string;
  dependencies?: string[];
  rules?: PermissionRule[];
};

export type CatalogOperation = {
  key: string;
  label: string;
  description?: string;
  type: "button" | "table";
  template: string;
  permission?: string;
  order?: number;
  dependencies?: string[];
  rules?: PermissionRule[];
};

export type CatalogMenu = {
  key: string;
  label: string;
  description?: string;
  path: string;
  icon?: string;
  group?: string;
  parent?: string;
  parentIcon?: string;
  component?: string;
  order?: number;
  publicMenu?: boolean;
  view?: CatalogLevel;
  manage?: CatalogLevel;
  operations?: CatalogOperation[];
};

export type CatalogWorkspace = {
  id: string;
  name: string;
  plan: string;
  icon?: string;
  order?: number;
  menus?: CatalogMenu[];
};

export type PermissionCatalog = {
  workspaces?: CatalogWorkspace[];
  resourceLabels?: Record<string, string>;
};

type NavigationOperation = {
  key: string;
  allowed: boolean;
};

export type NavigationMenu = {
  key: string;
  label: string;
  path: string;
  icon?: string;
  group?: string;
  parent?: string;
  parentIcon?: string;
  component?: string;
  order?: number;
  operations?: NavigationOperation[];
};

type NavigationWorkspace = {
  id: string;
  name: string;
  plan: string;
  icon?: string;
  order?: number;
  homePath: string;
  menus?: NavigationMenu[];
};

export type Navigation = {
  workspaces?: NavigationWorkspace[];
};

export async function fetchPermissionCatalog(): Promise<PermissionCatalog> {
  const response = await axiosInstance.get<PermissionCatalog>(`${API_PREFIX}/permission-catalog`);
  return response.data;
}

export async function fetchNavigation(): Promise<Navigation> {
  const response = await axiosInstance.get<Navigation>(`${API_PREFIX}/navigation`);
  return response.data;
}
