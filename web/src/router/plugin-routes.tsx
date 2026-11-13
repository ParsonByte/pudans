import type { ReactNode } from "react";
import { Navigate, useLocation, type RouteObject } from "react-router-dom";
import type {
  MenuMeta,
  PluginModule,
  PluginRoute,
  RegisteredMenu,
} from "@pudans/shared";

export interface PluginRouteRegistrationOptions {
  /** module.ts entries keyed by their Vite glob path, exactly like reference. */
  modules: Record<string, PluginModule>;
  platform: "console" | "uc";
  permissions: readonly string[];
  /** Fixed application base path, never derived from an environment variable. */
  basePath: "/console" | "/uc";
  /** Route name the layout route is registered under. */
  rootName: string;
  /** Render the layout after the permission-filtered menu is known. */
  renderLayout: (menu: readonly RegisteredMenu[]) => ReactNode;
  /** Rendered when no module route survives the permission filter. */
  emptyFallback: ReactNode;
  /** Rendered for a declared route that was filtered out by permissions. */
  deniedFallback: ReactNode;
  /** Rendered for an unknown URL. */
  notFoundFallback: ReactNode;
}

export interface PluginRouteRegistration {
  routes: RouteObject[];
  menu: RegisteredMenu[];
  deniedPaths: string[];
}

interface RouteNode {
  route: PluginRoute;
  fullPath: string;
  children: RouteNode[];
}

function hasPermission(permissions: readonly string[], required: readonly string[]): boolean {
  if (permissions.includes("*")) {
    return true;
  }
  return required.some((permission) => permissions.includes(permission));
}

function joinPath(parent: string, path: string): string {
  if (!path) {
    return parent;
  }
  if (path.startsWith("/")) {
    return path.replace(/\/+$/, "") || "/";
  }
  if (parent === "/") {
    return "/" + path.replace(/\/+$/, "");
  }
  return (parent + "/" + path).replace(/\/+$/, "");
}

function toRouterPath(path: string): string {
  return path.replace(/^\/+/, "");
}

/**
 * the reference route registration, translated to React Router: module routes are
 * filtered by their meta.permissions before they are registered, the menu is
 * collected from the same surviving set, and parentName attaches a route to
 * the layout route. A filtered route is remembered so a direct URL lands on
 * the forbidden fallback rather than silently disappearing.
 */
export function registerPluginRoutes({
  modules,
  platform,
  permissions,
  rootName,
  renderLayout,
  emptyFallback,
  deniedFallback,
  notFoundFallback,
}: PluginRouteRegistrationOptions): PluginRouteRegistration {
  const deniedPaths: string[] = [];
  const nodes: RouteNode[] = [];

  function filterRoutes(
    routes: readonly PluginRoute[] | undefined,
    parentPath: string,
  ): RouteNode[] {
    if (!routes) {
      return [];
    }

    return routes.flatMap((route) => {
      const fullPath = joinPath(parentPath, route.path);
      const required = route.meta?.permissions ?? [];
      if (required.length > 0 && !hasPermission(permissions, required)) {
        deniedPaths.push(fullPath);
        return [];
      }
      const children = filterRoutes(route.children, fullPath);
      return [{ route, fullPath, children }];
    });
  }

  for (const module of Object.values(modules)) {
    const routes = platform === "console" ? module.routes : module.ucRoutes;
    nodes.push(...filterRoutes(routes, "/"));
  }

  const menu = collectMenu(nodes);
  const routeObjects: RouteObject[] = [];
  const routeIndex = new Map<string, RouteObject>();

  for (const node of nodes) {
    const parentName = node.route.meta?.parentName ?? node.route.parentName;
    if (parentName && parentName !== rootName) {
      const parent = routeIndex.get(parentName);
      if (parent) {
        parent.children = [...(parent.children ?? []), toRouteObject(node)];
        continue;
      }
    }
    const object = toRouteObject(node);
    routeObjects.push(object);
    routeIndex.set(node.route.name, object);
  }

  const firstMenu = menu[0];
  const rootChildren: RouteObject[] = firstMenu
    ? [
        { index: true, element: <Navigate to={firstMenu.path.replace(/^\/+/, "")} replace /> },
        ...routeObjects,
      ]
    : [{ index: true, element: emptyFallback }, ...routeObjects];

  rootChildren.push({
    path: "*",
    element: <PermissionFallback deniedPaths={deniedPaths} denied={deniedFallback} notFound={notFoundFallback} />,
  });

  return {
    menu,
    deniedPaths,
    routes: [
      {
        path: "/",
        element: renderLayout(menu),
        children: rootChildren,
      },
    ],
  };
}

function toRouteObject(node: RouteNode): RouteObject {
  const Component = node.route.component;
  const children = node.children.map(toRouteObject);
  const handle = {
    title: node.route.meta?.title,
    menu: node.route.meta?.menu,
    permissions: node.route.meta?.permissions,
  };

  if (node.route.index) {
    return {
      index: true,
      element: Component ? <Component /> : undefined,
      handle,
    };
  }

  return {
    path: toRouterPath(node.route.path),
    element: Component ? <Component /> : undefined,
    handle,
    ...(node.route.redirect ? { element: <Navigate to={node.route.redirect} replace /> } : {}),
    ...(children.length > 0 ? { children } : {}),
  };
}

function collectMenu(nodes: readonly RouteNode[]): RegisteredMenu[] {
  const found: RegisteredMenu[] = [];

  function visit(node: RouteNode): void {
    const meta: MenuMeta | undefined = node.route.meta?.menu;
    if (meta) {
      found.push({
        name: meta.name,
        group: meta.group,
        icon: meta.icon,
        iconActive: meta.iconActive ?? meta.icon,
        priority: meta.priority ?? 100,
        mobile: meta.mobile ?? true,
        path: node.fullPath,
        href: node.fullPath,
        routeName: node.route.name,
      });
    }
    node.children.forEach(visit);
  }

  nodes.forEach(visit);
  return found.sort((left, right) => left.priority - right.priority || left.path.localeCompare(right.path));
}

function PermissionFallback({
  deniedPaths,
  denied,
  notFound,
}: {
  deniedPaths: readonly string[];
  denied: ReactNode;
  notFound: ReactNode;
}) {
  const location = useLocation();
  const path = location.pathname.replace(/\/+$/, "") || "/";
  const isDenied = deniedPaths.some((deniedPath) => path === deniedPath || path.startsWith(deniedPath + "/"));
  return <>{isDenied ? denied : notFound}</>;
}
