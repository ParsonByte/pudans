import type { ComponentType } from "react";

/** Menu metadata attached to a dynamic route (Halo's route meta.menu). */
export interface MenuMeta {
  /** Translation key resolved by the active i18n namespace. */
  name: string;
  /** Menu group key; groups are ordered by the smallest priority inside them. */
  group: string;
  /** Iconify identifier, for example "mdi:view-dashboard-outline". */
  icon: string;
  /** Lower values sort first. Defaults to 100. */
  priority?: number;
  /** Whether the item may appear in the mobile navigation. Defaults to true. */
  mobile?: boolean;
}

/** Route metadata: title, UI permissions and the sidebar menu entry. */
export interface RouteMeta {
  title?: string;
  permissions?: readonly string[];
  menu?: MenuMeta;
  /** Layout route this route attaches to (route-level parentName is also accepted). */
  parentName?: string;
}

/**
 * A route contributed by a core module. It mirrors Halo's plugin route
 * definition: a component, optional children and meta that can expose a menu
 * entry and a permission gate. Routes with a parentName are attached below the
 * layout route with that name.
 */
export interface PluginRoute {
  path: string;
  name: string;
  component?: ComponentType;
  redirect?: string;
  index?: boolean;
  children?: PluginRoute[];
  parentName?: string;
  meta?: RouteMeta;
}

/** Halo-style module definition (routes for console, ucRoutes for the UC). */
export interface PluginModule {
  routes?: PluginRoute[];
  ucRoutes?: PluginRoute[];
}

/** A menu entry produced from an already permission-filtered route. */
export interface RegisteredMenu {
  name: string;
  group: string;
  icon: string;
  priority: number;
  mobile: boolean;
  /** Router-relative path, for example "/users". */
  path: string;
  /** Router-relative link; the router basename adds /console or /uc. */
  href: string;
  routeName: string;
}
