import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, useLocation, useMatches, useNavigate, useOutlet } from "react-router-dom";
import { CSSTransition, SwitchTransition } from "react-transition-group";
import {
  AppBar,
  Avatar,
  Box,
  ButtonBase,
  Collapse,
  Divider,
  Drawer,
  Fade,
  IconButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  MenuList,
  Popover,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { darken, lighten } from "@mui/material/styles";
import { Icon, ThemeSwitch } from "@pudans/components";
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
  let lastGroup: string | undefined;
  for (const item of menu) {
    if (groups.length === 0 || item.group !== lastGroup) {
      groups.push([item]);
      lastGroup = item.group;
    } else {
      groups[groups.length - 1].push(item);
    }
  }
  return groups;
}

/**
 * Dashboard shell copied from the reference NavBarFrame:
 * - 240px persistent drawer over grey[100]/grey[900] with a 90px pill nav
 * - fixed 64px app bar sharing the drawer surface colour
 * - main column inset 16px on the right and pulled 16px under the drawer when
 *   the sidebar is closed, exactly like the reference AppMain margin
 * - pages own their bordered, rounded PageContainer (12px theme radius)
 * - var(--pd-duration-fast) opacity page switch, the shell never remounts
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
  const outlet = useOutlet();
  const navigate = useNavigate();
  const pageRef = useRef<HTMLDivElement>(null);
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down("sm"));
  const [sidebarOpen, setSidebarOpen] = useState(readSidebarOpen);
  const [headerHovered, setHeaderHovered] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [mobileAnchor, setMobileAnchor] = useState<HTMLElement | null>(null);
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

  const closeMobileMenu = (): void => {
    setMobileOpen(false);
    setMobileAnchor(null);
  };

  const openAccountSettings = (): void => {
    setUserMenuAnchor(null);
    if (namespace === "uc") {
      navigate("/profile");
    } else {
      navigateToApp("/uc/profile");
    }
  };

  /** useLocation inside a basename router is already stripped of the basename. */
  const isActive = (href: string): boolean => {
    const path = routerPath(href);
    return location.pathname === path || location.pathname.startsWith(path + "/");
  };

  /** Router links are resolved against the router basename, so strip basePath. */
  const routerPath = (href: string): string =>
    href === basePath ? "/" : href.startsWith(basePath + "/") ? href.slice(basePath.length) : href;

  const navItem = (item: RegisteredMenu) => {
    const active = isActive(item.href);
    const activeBackground =
      theme.palette.mode === "light"
        ? lighten(theme.palette.primary.main, 0.7)
        : darken(theme.palette.primary.main, 0.7);
    return (
      <ButtonBase
        key={item.href}
        component={NavLink}
        to={routerPath(item.href)}
        onClick={closeMobileMenu}
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
          backgroundColor: active ? activeBackground : "initial",
          transition: theme.transitions.create("background-color"),
          "&:hover": {
            backgroundColor: active ? activeBackground : theme.palette.action.hover,
          },
        }}
      >
        <Box
          sx={{
            position: "relative",
            width: 20,
            height: 20,
            mr: "14px",
            color: "action.active",
          }}
        >
          <Box
            sx={{
              position: "absolute",
              inset: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              opacity: active ? 0 : 1,
              transition: theme.transitions.create("opacity"),
            }}
          >
            <Icon name={item.icon} size={20} />
          </Box>
          <Box
            sx={{
              position: "absolute",
              inset: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              opacity: active ? 1 : 0,
              transition: theme.transitions.create("opacity"),
            }}
          >
            <Icon name={item.iconActive} size={20} />
          </Box>
        </Box>
        <Typography variant="body2" noWrap>
          {t(item.name)}
        </Typography>
      </ButtonBase>
    );
  };

  const switchItem = switchTarget ? (
    <ButtonBase
      onClick={() => {
        closeMobileMenu();
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
        transition: theme.transitions.create("background-color"),
        "&:hover": { backgroundColor: theme.palette.action.hover },
      }}
    >
      <Box
        sx={{
          width: 20,
          height: 20,
          mr: "14px",
          display: "flex",
          alignItems: "center",
          color: "action.active",
        }}
      >
        <Icon name={switchTarget.icon} size={20} />
      </Box>
      <Typography variant="body2" noWrap>
        {t(switchTarget.labelKey)}
      </Typography>
    </ButtonBase>
  ) : null;

  const drawerContent = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Box
        onMouseEnter={() => setHeaderHovered(true)}
        onMouseLeave={() => setHeaderHovered(false)}
        sx={{
          ...theme.mixins.toolbar,
          display: "flex",
          alignItems: "center",
          padding: theme.spacing(0, 1),
          justifyContent: "flex-end",
          flex: "0 0 auto",
        }}
      >
        <Box
          onClick={() => {
            closeMobileMenu();
            navigateToApp(basePath);
          }}
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
              borderRadius: 1,
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
            <Typography sx={{ fontWeight: 700, letterSpacing: "0.04em", fontSize: "1.25rem", lineHeight: 1.2 }} noWrap>
              {BRAND_WORDMARK}
            </Typography>
          </Box>
        </Box>
        {!mobile ? (
          <Fade in={headerHovered}>
            <IconButton
              aria-label={t("collapseSidebar")}
              onClick={() => setOpen(false)}
            >
              <Icon name="mdi:chevron-left" size={24} />
            </IconButton>
          </Fade>
        ) : null}
      </Box>

      <Stack
        direction="column"
        spacing={2}
        sx={{ px: 1, pb: 1, flexGrow: 1, mx: 1, overflow: "auto" }}
      >
        {groups.map((group) => (
          <Box key={group[0].group}>{group.map((item) => navItem(item))}</Box>
        ))}
        {switchTarget ? <Box>{switchItem}</Box> : null}
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
      {!mobile ? (
        <Drawer
          variant="persistent"
          anchor="left"
          open={sidebarOpen}
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            "& .MuiDrawer-paper": {
              width: DRAWER_WIDTH,
              boxSizing: "border-box",
              backgroundColor: shellBg,
              borderRight: "initial",
            },
          }}
        >
          {drawerContent}
        </Drawer>
      ) : null}

      <AppBar
        position="fixed"
        elevation={0}
        enableColorOnDark
        sx={{
          width: mobile || !sidebarOpen ? "100%" : `calc(100% - ${DRAWER_WIDTH}px)`,
          ml: mobile || !sidebarOpen ? 0 : `${DRAWER_WIDTH}px`,
          backgroundColor: shellBg,
          color: theme.palette.getContrastText(shellBg),
          boxShadow: "none",
          transition: theme.transitions.create(["width", "margin"], {
            easing: sidebarOpen ? theme.transitions.easing.easeOut : theme.transitions.easing.sharp,
            duration: sidebarOpen
              ? theme.transitions.duration.enteringScreen
              : theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Toolbar
          sx={{
            paddingLeft: !mobile && sidebarOpen ? 0 : theme.spacing(mobile ? 2 : 3),
            transition: theme.transitions.create("padding", {
              easing: theme.transitions.easing.easeInOut,
              duration: theme.transitions.duration.standard,
            }),
          }}
        >
          <Collapse orientation="horizontal" in={mobile || !sidebarOpen}>
            <IconButton
              color="inherit"
              aria-label={t("openSidebar")}
              edge="start"
              onClick={(event) => {
                if (mobile) {
                  setMobileAnchor(event.currentTarget);
                  setMobileOpen(true);
                } else {
                  setOpen(true);
                }
              }}
              sx={{ mr: mobile ? 1 : 2, ml: mobile ? -1 : -1.5 }}
            >
              <Icon name="mdi:menu" size={24} />
            </IconButton>
          </Collapse>
          <Box sx={{ flexGrow: 1 }} />
          <Stack
            direction="row"
            spacing={mobile ? 1 : 0}
            sx={{
              alignItems: "center",
            }}
          >
            {!mobile ? (
              <>
                <ThemeSwitch
                  aria-label={t("theme")}
                  labels={{
                    light: t("themeLight"),
                    system: t("themeSystem"),
                    dark: t("themeDark"),
                  }}
                />
                <Tooltip title={t("accountSettings")}>
                  <IconButton
                    size="large"
                    aria-label={t("accountSettings")}
                    onClick={openAccountSettings}
                  >
                    <Icon name="mdi:cog-outline" size={24} />
                  </IconButton>
                </Tooltip>
              </>
            ) : null}
            <IconButton
              size="large"
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
          </Stack>
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          height: "100svh",
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          ml: mobile ? 0 : sidebarOpen ? 0 : `-${DRAWER_WIDTH - CONTENT_OVERLAP}px`,
          mr: mobile ? 0 : 2,
          transition: theme.transitions.create("margin", {
            easing: sidebarOpen ? theme.transitions.easing.easeOut : theme.transitions.easing.sharp,
            duration: sidebarOpen
              ? theme.transitions.duration.enteringScreen
              : theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Box sx={{ ...theme.mixins.toolbar, flexShrink: 0 }} />
        <SwitchTransition mode="out-in">
          <CSSTransition
            key={namespace === "uc" ? "uc-settings" : location.pathname}
            nodeRef={pageRef}
            timeout={150}
            classNames={{
              enter: styles.pageEnter,
              enterActive: styles.pageEnterActive,
              exit: styles.pageExit,
              exitActive: styles.pageExitActive,
            }}
          >
            <Box
              ref={pageRef}
              sx={{
                flexGrow: 1,
                minHeight: 0,
                display: "flex",
                flexDirection: "column",
                overflow: "hidden",
              }}
            >
              {outlet}
            </Box>
          </CSSTransition>
        </SwitchTransition>
      </Box>

      {mobile ? (
        <Popover
          open={mobileOpen}
          anchorEl={mobileAnchor}
          onClose={closeMobileMenu}
          anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
          transformOrigin={{ vertical: "top", horizontal: "left" }}
          slotProps={{
            paper: {
              sx: {
                width: "70vw",
                maxHeight: "calc(100vh - 64px)",
                display: "flex",
                flexDirection: "column",
              },
            },
          }}
        >
          {drawerContent}
        </Popover>
      ) : null}

      <Popover
        open={Boolean(userMenuAnchor)}
        anchorEl={userMenuAnchor}
        onClose={() => setUserMenuAnchor(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Box
          sx={{
            px: "12px",
            py: "10px",
            minWidth: "230px",
            display: "flex",
            maxWidth: "300px",
            width: "100%",
            flexDirection: "column",
            gap: "4px",
          }}
        >
          <Box sx={{ display: "flex", minWidth: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1 }} noWrap>
              {displayName}
            </Typography>
            <Typography variant="body2" sx={{ ml: 1, lineHeight: 1 }} color="textSecondary" noWrap>
              {t(brandSubKey)}
            </Typography>
          </Box>
          <Typography variant="caption" color="textSecondary" noWrap>
            {session?.username}
          </Typography>
        </Box>
        <Divider />
        <MenuList dense sx={{ mx: 0.5 }}>
          {switchTarget ? (
            <MenuItem
              onClick={() => {
                setUserMenuAnchor(null);
                navigateToApp(switchTarget.href);
              }}
            >
              <ListItemIcon>
                <Icon name={switchTarget.icon} size={20} />
              </ListItemIcon>
              <ListItemText>{t(switchTarget.labelKey)}</ListItemText>
            </MenuItem>
          ) : null}
          <MenuItem onClick={openAccountSettings}>
            <ListItemIcon>
              <Icon name="mdi:cog-outline" size={20} />
            </ListItemIcon>
            <ListItemText>{t("accountSettings")}</ListItemText>
          </MenuItem>
          <Divider sx={{ my: 0.5 }} />
          <MenuItem
            selected={currentLanguage === "zh-CN"}
            onClick={() => {
              setUserMenuAnchor(null);
              dispatch(setLocale("zh-CN"));
              void i18n.changeLanguage("zh-CN");
            }}
          >
            <ListItemIcon>
              <Icon name="mdi:translate" size={20} />
            </ListItemIcon>
            <ListItemText>{t("languageZh")}</ListItemText>
          </MenuItem>
          <MenuItem
            selected={currentLanguage === "en-US"}
            onClick={() => {
              setUserMenuAnchor(null);
              dispatch(setLocale("en-US"));
              void i18n.changeLanguage("en-US");
            }}
          >
            <ListItemIcon>
              <Icon name="mdi:translate" size={20} />
            </ListItemIcon>
            <ListItemText>{t("languageEn")}</ListItemText>
          </MenuItem>
          <Divider sx={{ my: 0.5 }} />
          <MenuItem
            onClick={() => {
              setUserMenuAnchor(null);
              beginLogout();
              void signOut()
                .catch(() => undefined)
                .finally(() => navigateToApp("/auth/signed-in"));
            }}
          >
            <ListItemIcon>
              <Icon name="mdi:logout" size={20} />
            </ListItemIcon>
            <ListItemText>{t("logout")}</ListItemText>
          </MenuItem>
        </MenuList>
      </Popover>
    </Box>
  );
}
