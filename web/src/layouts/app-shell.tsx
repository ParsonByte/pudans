import { Fragment, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useMatches } from "react-router-dom";
import {
  AppBar,
  Avatar,
  Box,
  ButtonBase,
  Container,
  Divider,
  Drawer,
  Fade,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { darken, lighten } from "@mui/material/styles";
import { Icon, LanguageSwitcher, ThemeSwitch } from "@pudans/components";
import type { RegisteredMenu } from "@pudans/shared";
import { BRAND_MARK, BRAND_NAME, BRAND_WORDMARK } from "../constants";
import { navigateToApp } from "../hooks/use-auth-navigation";
import { setLocale } from "../redux/app-slice";
import { useAppDispatch } from "../redux/store";
import { beginLogout } from "../session/logout-state";
import { useSession } from "../session/use-session";
import styles from "./app-shell.module.css";

export interface ShellSwitchTarget {
  href: string;
  labelKey: string;
  icon: string;
}

export interface AppShellProps {
  namespace: "console" | "uc";
  basePath: "/console" | "/uc";
  menu: readonly RegisteredMenu[];
  brandSubKey: string;
  switchTarget?: ShellSwitchTarget;
}

const DRAWER_WIDTH = 240;
const CONTENT_OVERLAP = 16;
const SIDEBAR_STORAGE_KEY = "pudans:shell:sidebar-open";

function readSidebarOpen(): boolean {
  if (typeof window === "undefined") {
    return true;
  }
  try {
    return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) !== "0";
  } catch {
    return true;
  }
}

function groupMenu(menu: readonly RegisteredMenu[]): RegisteredMenu[][] {
  const groups: RegisteredMenu[][] = [];
  for (const item of menu) {
    const group = groups.find((candidate) => candidate[0]?.group === item.group);
    if (group) {
      group.push(item);
    } else {
      groups.push([item]);
    }
  }
  return groups;
}

/**
 * Dashboard shell:
 * - 240px flat sidebar on grey[100]/grey[900] with 90px pill navigation
 * - fixed 64px app bar with the same surface colour
 * - rounded bordered page frame inset by 8px, page header inside
 * - var(--pd-duration-fast) opacity page switch, no page remount of the shell
 */
export function AppShell({
  namespace,
  basePath,
  menu,
  brandSubKey,
  switchTarget,
}: AppShellProps) {
  const { t, i18n } = useTranslation(namespace);
  const { session, signOut } = useSession();
  const location = useLocation();
  const matches = useMatches();
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down("sm"));
  const [sidebarOpen, setSidebarOpen] = useState(readSidebarOpen);
  const [headerHovered, setHeaderHovered] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null);

  const groups = useMemo(() => groupMenu(menu), [menu]);
  const titles = useMemo(
    () =>
      matches
        .map((match) => (match.handle as { title?: string } | undefined)?.title)
        .filter((title): title is string => typeof title === "string" && title.length > 0)
        .map((title) => t(title)),
    [matches, t],
  );
  const pageTitle = titles[titles.length - 1] ?? t(brandSubKey);
  const displayName = session?.displayName || session?.username || "";
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";
  const shellBg = theme.palette.mode === "light" ? theme.palette.grey[100] : theme.palette.grey[900];

  useEffect(() => {
    document.title = `${pageTitle} · ${BRAND_NAME}`;
  }, [pageTitle]);

  useEffect(() => {
    if (mobile) {
      setMobileOpen(false);
    }
  }, [location.pathname, mobile]);

  const setOpen = (open: boolean): void => {
    setSidebarOpen(open);
    try {
      window.localStorage.setItem(SIDEBAR_STORAGE_KEY, open ? "1" : "0");
    } catch {
      // Preference persistence is best effort.
    }
  };

  const isActive = (href: string): boolean =>
    href === "/" ? location.pathname === href : location.pathname === href || location.pathname.startsWith(href + "/");

  const navItem = (item: RegisteredMenu) => {
    const active = isActive(item.href);
    return (
      <ButtonBase
        key={item.href}
        component={NavLink}
        to={item.href}
        onClick={() => setMobileOpen(false)}
        aria-current={active ? "page" : undefined}
        focusRipple
        sx={{
          width: "100%",
          height: 32,
          display: "flex",
          justifyContent: "flex-start",
          alignItems: "center",
          padding: "4px",
          paddingLeft: "28px",
          borderRadius: "90px",
          backgroundColor: active
            ? theme.palette.mode === "light"
              ? lighten(theme.palette.primary.main, 0.7)
              : darken(theme.palette.primary.main, 0.7)
            : "initial",
          transition: theme.transitions.create("background-color"),
          "&:hover": {
            backgroundColor: active
              ? theme.palette.mode === "light"
                ? lighten(theme.palette.primary.main, 0.7)
                : darken(theme.palette.primary.main, 0.7)
              : theme.palette.action.hover,
          },
        }}
      >
        <Box sx={{ width: 20, height: 20, mr: "14px", display: "flex", alignItems: "center", color: active ? "primary.main" : "action.active" }}>
          <Icon name={item.icon} size={20} />
        </Box>
        <Typography variant="body2" noWrap sx={{ color: active ? "text.primary" : "text.secondary" }}>
          {t(item.name)}
        </Typography>
      </ButtonBase>
    );
  };

  const drawerContent = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%", backgroundColor: shellBg }}>
      <Box
        onMouseEnter={() => setHeaderHovered(true)}
        onMouseLeave={() => setHeaderHovered(false)}
        sx={{
          display: "flex",
          alignItems: "center",
          minHeight: 64,
          padding: "0 8px",
          flex: "0 0 auto",
        }}
      >
        <Box
          onClick={() => navigateToApp(basePath)}
          sx={{
            width: "100%",
            pl: 2,
            display: "flex",
            alignItems: "center",
            gap: 1.25,
            cursor: "pointer",
            minWidth: 0,
          }}
        >
          <Box
            aria-hidden="true"
            sx={{
              display: "grid",
              placeItems: "center",
              width: 34,
              height: 34,
              borderRadius: 2,
              backgroundColor: "primary.main",
              color: "primary.contrastText",
              fontSize: "0.9375rem",
              fontWeight: 700,
              flex: "0 0 auto",
            }}
          >
            {BRAND_MARK}
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 700, letterSpacing: "0.10em", fontSize: "0.9375rem", lineHeight: 1.2 }} noWrap>
              {BRAND_WORDMARK}
            </Typography>
            <Typography sx={{ color: "text.secondary", fontSize: "0.75rem", lineHeight: 1.2 }} noWrap>
              {t(brandSubKey)}
            </Typography>
          </Box>
        </Box>
        {!mobile ? (
          <Fade in={headerHovered}>
            <IconButton
              size="small"
              aria-label={t("collapseSidebar")}
              onClick={() => setOpen(false)}
              sx={{ color: "text.secondary" }}
            >
              <Icon name="mdi:chevron-left" size={20} />
            </IconButton>
          </Fade>
        ) : null}
      </Box>

      <Stack
        direction="column"
        spacing={2}
        sx={{ px: 1, pb: 1, flexGrow: 1, mx: 1, overflowY: "auto", overflowX: "hidden" }}
      >
        {groups.map((group) => (
          <Box key={group[0].group}>{group.map((item) => navItem(item))}</Box>
        ))}
        {switchTarget ? (
          <Box>
            <ButtonBase
              onClick={() => {
                setMobileOpen(false);
                navigateToApp(switchTarget.href);
              }}
              focusRipple
              sx={{
                width: "100%",
                height: 32,
                display: "flex",
                justifyContent: "flex-start",
                alignItems: "center",
                padding: "4px",
                paddingLeft: "28px",
                borderRadius: "90px",
                "&:hover": { backgroundColor: "action.hover" },
              }}
            >
              <Box sx={{ width: 20, height: 20, mr: "14px", display: "flex", alignItems: "center", color: "action.active" }}>
                <Icon name={switchTarget.icon} size={20} />
              </Box>
              <Typography variant="body2" noWrap sx={{ color: "text.secondary" }}>
                {t(switchTarget.labelKey)}
              </Typography>
            </ButtonBase>
          </Box>
        ) : null}
      </Stack>
    </Box>
  );

  return (
    <Box
      sx={{
        display: "flex",
        height: "100svh",
        overflow: "hidden",
        backgroundColor: shellBg,
      }}
    >
      {mobile ? (
        <Drawer
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{ "& .MuiDrawer-paper": { width: DRAWER_WIDTH, boxSizing: "border-box", borderRight: "initial" } }}
        >
          {drawerContent}
        </Drawer>
      ) : sidebarOpen ? (
        <Box
          component="aside"
          sx={{
            position: "fixed",
            top: 0,
            bottom: 0,
            left: 0,
            width: DRAWER_WIDTH,
            zIndex: theme.zIndex.drawer,
            backgroundColor: shellBg,
            overflow: "hidden",
          }}
        >
          {drawerContent}
        </Box>
      ) : null}

      <AppBar
        position="fixed"
        elevation={0}
        enableColorOnDark
        sx={{
          width: mobile || !sidebarOpen ? "100%" : `calc(100% - ${DRAWER_WIDTH}px)`,
          ml: mobile || !sidebarOpen ? 0 : `${DRAWER_WIDTH}px`,
          backgroundColor: shellBg,
          color: "text.primary",
          boxShadow: "none",
          transition: theme.transitions.create(["width", "margin"], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Toolbar
          sx={{
            minHeight: "64px !important",
            pl: !mobile && sidebarOpen ? 0 : { xs: 1, sm: 2 },
            pr: { xs: 1, sm: 2 },
          }}
        >
          {mobile || !sidebarOpen ? (
            <IconButton
              color="inherit"
              aria-label={t("openSidebar")}
              edge="start"
              onClick={() => (mobile ? setMobileOpen(true) : setOpen(true))}
              sx={{ mr: 1, ml: mobile ? -1 : -1.5 }}
            >
              <Icon name="mdi:menu-open" size={22} />
            </IconButton>
          ) : null}
          <Box sx={{ flexGrow: 1 }} />
          <Stack direction="row" spacing={0} sx={{ alignItems: "center" }}>
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
              size="large"
              aria-label={t("account")}
              aria-haspopup="menu"
              aria-expanded={Boolean(userMenuAnchor)}
              onClick={(event) => setUserMenuAnchor(event.currentTarget)}
            >
              <Avatar
                src={session?.avatar || undefined}
                sx={{ width: 30, height: 30, backgroundColor: "primary.main", color: "primary.contrastText", fontSize: "0.875rem" }}
              >
                {(displayName || "?").slice(0, 1).toUpperCase()}
              </Avatar>
            </IconButton>
          </Stack>
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          height: "100svh",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          ml: mobile ? 0 : sidebarOpen ? `${DRAWER_WIDTH - CONTENT_OVERLAP}px` : 0,
          mr: mobile ? 0 : 1,
          mb: mobile ? 0 : 1,
          transition: theme.transitions.create("margin", {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Toolbar sx={{ minHeight: "64px !important" }} />
        <Box
          className={styles.pageFrame}
          sx={{
            flexGrow: 1,
            minHeight: 0,
            overflowY: "auto",
            overflowX: "hidden",
            borderRadius: mobile ? 0 : 3,
            border: mobile ? 0 : 1,
            borderColor: "divider",
            backgroundColor: "background.paper",
          }}
        >
          <Container maxWidth="xl" sx={{ py: 4 }}>
            <Box sx={{ mb: 4 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 0.5, color: "text.secondary" }}>
                {[t(brandSubKey), ...titles].map((crumb, index, all) => (
                  <Fragment key={crumb + index}>
                    <Typography variant="body2" sx={{ color: index === all.length - 1 ? "text.primary" : "text.secondary" }}>
                      {crumb}
                    </Typography>
                    {index < all.length - 1 ? <Icon name="mdi:chevron-right" size={14} /> : null}
                  </Fragment>
                ))}
              </Box>
              <Typography variant="h4" sx={{ fontWeight: 600 }}>
                {pageTitle}
              </Typography>
            </Box>
            <Box key={location.pathname} className={styles.page}>
              <Outlet />
            </Box>
          </Container>
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
            <ListItemText>{t(switchTarget.labelKey)}</ListItemText>
          </MenuItem>
        ) : null}
        <MenuItem
          onClick={() => {
            setUserMenuAnchor(null);
            beginLogout();
            void signOut()
              .catch(() => undefined)
              .finally(() => navigateToApp("/auth/signed-in"));
          }}
        >
          <ListItemIcon sx={{ minWidth: 32 }}>
            <Icon name="mdi:logout" size={16} />
          </ListItemIcon>
          <ListItemText>{t("logout")}</ListItemText>
        </MenuItem>
      </Menu>
    </Box>
  );
}
