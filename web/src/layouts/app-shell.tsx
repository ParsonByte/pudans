import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useMatches } from "react-router-dom";
import {
  Avatar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { Breadcrumbs, Icon, LanguageSwitcher, ThemeSwitch } from "@pudans/components";
import type { RegisteredMenu } from "@pudans/shared";
import { BRAND_MARK, BRAND_WORDMARK } from "../constants";
import { navigateToApp } from "../hooks/use-auth-navigation";
import { setLocale } from "../redux/app-slice";
import { useAppDispatch } from "../redux/store";
import { useSession } from "../session/use-session";

export interface ShellSwitchTarget {
  href: string;
  labelKey: string;
  icon: string;
}

export interface AppShellProps {
  namespace: "console" | "uc";
  basePath: "/console" | "/uc";
  menu: readonly RegisteredMenu[];
  groupLabels: Readonly<Record<string, string>>;
  brandSubKey: string;
  switchTarget?: ShellSwitchTarget;
}

const SIDEBAR_STORAGE_KEY = "pudans:shell:sidebar-collapsed";

function readCollapsed(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

function groupKeyFor(menu: RegisteredMenu, groupLabels: Record<string, string>): string {
  return groupLabels[menu.group] ?? menu.group;
}

/**
 * fixed-sidebar application shell: a fixed left sidebar with grouped menu
 * entries, a top bar with breadcrumb/title and the user menu, and a content
 * region that owns its own scrolling. Every menu item comes from the dynamic
 * route registration; the shell never declares a product route itself.
 */
export function AppShell({
  namespace,
  basePath,
  menu,
  groupLabels,
  brandSubKey,
  switchTarget,
}: AppShellProps) {
  const { t, i18n } = useTranslation(namespace);
  const { session, signOut } = useSession();
  const location = useLocation();
  const matches = useMatches();
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down("md"));
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null);

  const groups = Array.from(new Set(menu.map((item) => item.group))).sort((left, right) => {
    const leftPriority = Math.min(...menu.filter((item) => item.group === left).map((item) => item.priority));
    const rightPriority = Math.min(...menu.filter((item) => item.group === right).map((item) => item.priority));
    return leftPriority - rightPriority;
  });

  const currentTitle = [...matches]
    .reverse()
    .map((match) => (match.handle as { title?: string } | undefined)?.title)
    .find((title): title is string => typeof title === "string" && title.length > 0);

  const displayName = session?.displayName || session?.username || "";
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";

  const toggleCollapsed = (): void => {
    setCollapsed((current) => {
      const next = !current;
      try {
        window.localStorage.setItem(SIDEBAR_STORAGE_KEY, next ? "1" : "0");
      } catch {
        // Preference persistence is best-effort.
      }
      return next;
    });
  };

  const isActive = (href: string): boolean => {
    if (href === "/") {
      return location.pathname === href;
    }
    return location.pathname === href || location.pathname.startsWith(href + "/");
  };

  const sidebarWidth = collapsed ? "var(--pd-shell-drawer-collapsed-width)" : "var(--pd-shell-drawer-width)";

  const navigation = (
    <Box
      component="nav"
      aria-label={t(brandSubKey)}
      sx={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden" }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: collapsed ? 0 : 1.25,
          justifyContent: collapsed ? "center" : "flex-start",
          px: collapsed ? 0 : 2.5,
          py: 2.5,
          minHeight: "var(--pd-shell-app-bar-height)",
        }}
      >
        <Box
          aria-hidden="true"
          sx={{
            display: "grid",
            placeItems: "center",
            flex: "0 0 auto",
            width: 34,
            height: 34,
            borderRadius: 2,
            backgroundColor: "primary.main",
            color: "primary.contrastText",
            fontSize: "0.9375rem",
            fontWeight: 700,
          }}
        >
          {BRAND_MARK}
        </Box>
        {!collapsed ? (
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 700, letterSpacing: "0.12em", fontSize: "0.8125rem" }} noWrap>
              {BRAND_WORDMARK}
            </Typography>
            <Typography sx={{ color: "text.secondary", fontSize: "0.75rem" }} noWrap>
              {t(brandSubKey)}
            </Typography>
          </Box>
        ) : null}
      </Box>
      <Divider />

      <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden", px: collapsed ? 0.75 : 1.25, py: 1.5 }}>
        {groups.map((group) => {
          const items = menu.filter((item) => item.group === group);
          return (
            <Box key={group} component="section" aria-label={t(groupKeyFor(items[0], { ...groupLabels }))} sx={{ mb: 1.5 }}>
              {!collapsed ? (
                <Typography
                  component="h2"
                  sx={{
                    px: 1.5,
                    mb: 0.5,
                    color: "text.secondary",
                    fontSize: "0.6875rem",
                    fontWeight: 700,
                    letterSpacing: "0.08em",
                    textTransform: "uppercase",
                  }}
                >
                  {t(groupKeyFor(items[0], { ...groupLabels }))}
                </Typography>
              ) : null}
              <List disablePadding>
                {items.map((item) => {
                  const active = isActive(item.href);
                  const button = (
                    <ListItemButton
                      key={item.href}
                      component={NavLink}
                      to={item.href}
                      selected={active}
                      onClick={() => setMobileOpen(false)}
                      aria-current={active ? "page" : undefined}
                      sx={{
                        justifyContent: collapsed ? "center" : "flex-start",
                        minHeight: 40,
                        mb: 0.25,
                        px: collapsed ? 0 : 1.5,
                        gap: 1.25,
                        borderRadius: 2,
                        color: active ? "primary.main" : "text.secondary",
                        backgroundColor: active ? "action.selected" : "transparent",
                        "&:hover": { backgroundColor: "action.hover" },
                        "&.Mui-selected": { backgroundColor: "action.selected" },
                        "&.Mui-selected:hover": { backgroundColor: "action.selected" },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: 0, color: "inherit" }}>
                        <Icon name={item.icon} size={20} />
                      </ListItemIcon>
                      {!collapsed ? (
                        <ListItemText
                          primary={
                            <Typography
                              component="span"
                              sx={{ display: "block", fontSize: "0.875rem", fontWeight: active ? 600 : 500 }}
                              noWrap
                            >
                              {t(item.name)}
                            </Typography>
                          }
                        />
                      ) : null}
                    </ListItemButton>
                  );
                  return collapsed ? (
                    <Tooltip key={item.href} title={t(item.name)} placement="right">
                      {button}
                    </Tooltip>
                  ) : (
                    button
                  );
                })}
              </List>
            </Box>
          );
        })}
      </Box>

      {!mobile ? (
        <Box sx={{ px: collapsed ? 0.75 : 1.25, py: 1.5, borderTop: 1, borderColor: "divider" }}>
          <Tooltip title={collapsed ? t("expandSidebar") : t("collapseSidebar")} placement="right">
            <IconButton
              aria-label={collapsed ? t("expandSidebar") : t("collapseSidebar")}
              aria-expanded={!collapsed}
              onClick={toggleCollapsed}
              sx={{ width: "100%", borderRadius: 2, color: "text.secondary" }}
            >
              <Icon name={collapsed ? "mdi:chevron-right" : "mdi:chevron-left"} size={20} />
            </IconButton>
          </Tooltip>
        </Box>
      ) : null}
    </Box>
  );

  return (
    <Box sx={{ display: "flex", height: "100svh", overflow: "hidden", backgroundColor: "background.default" }}>
      {mobile ? (
        <Drawer
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{ "& .MuiDrawer-paper": { width: "var(--pd-shell-drawer-width)", boxSizing: "border-box" } }}
        >
          {navigation}
        </Drawer>
      ) : (
        <Box
          component="aside"
          sx={{
            flex: "0 0 auto",
            width: sidebarWidth,
            height: "100%",
            borderRight: 1,
            borderColor: "divider",
            backgroundColor: "background.paper",
            overflow: "hidden",
            transition: theme.transitions.create("width", {
              duration: theme.transitions.duration.standard,
              easing: theme.transitions.easing.easeInOut,
            }),
          }}
        >
          {navigation}
        </Box>
      )}

      <Box sx={{ display: "flex", flexDirection: "column", flex: 1, minWidth: 0, height: "100%", overflow: "hidden" }}>
        <Box
          component="header"
          sx={{
            flex: "0 0 auto",
            display: "flex",
            alignItems: "center",
            gap: 1.5,
            minHeight: "var(--pd-shell-app-bar-height)",
            px: { xs: 1.5, md: 3 },
            borderBottom: 1,
            borderColor: "divider",
            backgroundColor: "background.paper",
          }}
        >
          {mobile ? (
            <IconButton aria-label={t("openSidebar")} onClick={() => setMobileOpen(true)}>
              <Icon name="mdi:menu-open" size={22} />
            </IconButton>
          ) : null}

          <Breadcrumbs
            ariaLabel={t("breadcrumb")}
            items={[
              { key: "app", label: t(brandSubKey), icon: "mdi:home-outline", href: basePath },
              ...(currentTitle ? [{ key: "page", label: t(currentTitle) }] : []),
            ]}
          />

          <Box sx={{ flex: 1 }} />

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
              dispatch(setLocale(locale));
              void i18n.changeLanguage(locale);
            }}
          />

          <IconButton
            aria-label={t("account")}
            aria-haspopup="menu"
            aria-expanded={Boolean(userMenuAnchor)}
            onClick={(event) => setUserMenuAnchor(event.currentTarget)}
          >
            <Avatar
              src={session?.avatar || undefined}
              sx={{
                width: 30,
                height: 30,
                backgroundColor: "primary.main",
                color: "primary.contrastText",
                fontSize: "0.875rem",
              }}
            >
              {(displayName || "?").slice(0, 1).toUpperCase()}
            </Avatar>
          </IconButton>
        </Box>

        <Box component="main" sx={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden" }}>
          <Box sx={{ px: { xs: 2, md: 3 }, py: { xs: 2, md: 3 }, maxWidth: 1440, mx: "auto" }}>
            <Outlet />
          </Box>
        </Box>
      </Box>

      <Menu
        anchorEl={userMenuAnchor}
        open={Boolean(userMenuAnchor)}
        onClose={() => setUserMenuAnchor(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <MenuItem disabled sx={{ opacity: "1 !important" }}>
          <Box sx={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
            <Typography sx={{ fontWeight: 600, fontSize: "0.875rem" }} noWrap>
              {displayName}
            </Typography>
            <Typography sx={{ color: "text.secondary", fontSize: "0.75rem" }} noWrap>
              {session?.username}
            </Typography>
          </Box>
        </MenuItem>
        <Divider />
        {switchTarget ? (
          <MenuItem
            onClick={() => {
              setUserMenuAnchor(null);
              navigateToApp(switchTarget.href);
            }}
          >
            <ListItemIcon sx={{ minWidth: 32 }}>
              <Icon name={switchTarget.icon} size={16} />
            </ListItemIcon>
            <ListItemText>
              <Typography component="span" sx={{ fontSize: "0.875rem" }}>
                {t(switchTarget.labelKey)}
              </Typography>
            </ListItemText>
          </MenuItem>
        ) : null}
        <MenuItem
          onClick={() => {
            setUserMenuAnchor(null);
            void signOut().finally(() => navigateToApp("/auth/"));
          }}
        >
          <ListItemIcon sx={{ minWidth: 32 }}>
            <Icon name="mdi:logout" size={16} />
          </ListItemIcon>
          <ListItemText>
            <Typography component="span" sx={{ fontSize: "0.875rem" }}>
              {t("logout")}
            </Typography>
          </ListItemText>
        </MenuItem>
      </Menu>
    </Box>
  );
}

export type ShellContentProps = { children?: ReactNode };
