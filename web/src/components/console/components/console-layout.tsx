import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import {
  AppBar,
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
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { Icon, LanguageSwitcher, ThemeSwitch } from "@pudans/components";
import { useAppDispatch } from "../../../redux/store";
import { setLocale } from "../../../redux/app-slice";
import { useSession } from "../../../session/use-session";
import { useLogout } from "../composables/use-logout";
import { BRAND_MARK, BRAND_WORDMARK } from "../../../constants";

const DRAWER_WIDTH = 264;

const NAV_ITEMS = [
  { to: "/console", labelKey: "navOverview", icon: "mdi:view-dashboard-outline", permission: "dashboard:view" },
  { to: "/console/users", labelKey: "navUsers", icon: "mdi:account-multiple-outline", permission: "users:manage" },
  { to: "/console/audit", labelKey: "navAudit", icon: "mdi:file-document-outline", permission: "audit:view" },
] as const;

export function ConsoleLayout() {
  const { t, i18n } = useTranslation("console");
  const { session, hasPermission } = useSession();
  const logout = useLogout();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down("md"));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null);

  const visibleItems = NAV_ITEMS.filter((item) => hasPermission(item.permission));
  const current = [...NAV_ITEMS].reverse().find((item) => location.pathname === item.to || location.pathname.startsWith(item.to + "/"));
  const titleKey = current?.labelKey ?? "navOverview";
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";

  const changeLanguage = async (locale: string): Promise<void> => {
    dispatch(setLocale(locale));
    await i18n.changeLanguage(locale);
  };

  const drawer = (
    <Box role="navigation" aria-label={t("brandSub")} sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, px: 2.5, py: 2.5 }}>
        <Box
          aria-hidden="true"
          sx={{
            display: "grid",
            placeItems: "center",
            width: 34,
            height: 34,
            borderRadius: 2,
            bgcolor: "primary.main",
            color: "primary.contrastText",
            fontWeight: 700,
          }}
        >
          {BRAND_MARK}
        </Box>
        <Box>
          <Typography sx={{ fontWeight: 700, letterSpacing: "0.12em", fontSize: "0.875rem" }}>{BRAND_WORDMARK}</Typography>
          <Typography sx={{ color: "text.secondary", fontSize: "0.75rem" }}>{t("brandSub")}</Typography>
        </Box>
      </Box>
      <Divider />
      <List sx={{ px: 1.25, py: 1.5 }}>
        {visibleItems.map((item) => {
          const active = location.pathname === item.to || location.pathname.startsWith(item.to + "/");
          return (
            <ListItemButton
              key={item.to}
              component={NavLink}
              to={item.to}
              end={item.to === "/console"}
              selected={active}
              onClick={() => setMobileOpen(false)}
              sx={{ borderRadius: 2, mb: 0.5, gap: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 0 }}>
                <Icon name={item.icon} size={20} />
              </ListItemIcon>
              <ListItemText primary={<Typography sx={{ fontSize: "0.875rem", fontWeight: active ? 600 : 500 }}>{t(item.labelKey)}</Typography>} />
            </ListItemButton>
          );
        })}
      </List>
      <Box sx={{ mt: "auto", px: 1.25, pb: 2 }}>
        <Divider sx={{ mb: 1.25 }} />
        <ListItemButton component={NavLink} to="/uc" sx={{ borderRadius: 2, gap: 1 }}>
          <ListItemIcon sx={{ minWidth: 0 }}>
            <Icon name="mdi:home-outline" size={20} />
          </ListItemIcon>
          <ListItemText primary={<Typography sx={{ fontSize: "0.875rem" }}>{t("backToUc")}</Typography>} />
        </ListItemButton>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100svh", bgcolor: "background.default" }}>
      <AppBar position="fixed" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: "divider", zIndex: theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 1.5 }}>
          {mobile ? (
            <IconButton aria-label={t("brandSub")} onClick={() => setMobileOpen((open) => !open)}>
              <Icon name="mdi:menu" size={22} />
            </IconButton>
          ) : null}
          <Typography component="h1" sx={{ fontWeight: 600, fontSize: "1rem" }}>{t(titleKey)}</Typography>
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
              void changeLanguage(locale);
            }}
          />
          <IconButton aria-label={t("account")} onClick={(event) => setUserMenuAnchor(event.currentTarget)}>
            <Avatar sx={{ width: 30, height: 30, bgcolor: "primary.main", color: "primary.contrastText", fontSize: "0.875rem" }}>
              {(session?.displayName || session?.username || "?").slice(0, 1).toUpperCase()}
            </Avatar>
          </IconButton>
        </Toolbar>
      </AppBar>

      <Menu anchorEl={userMenuAnchor} open={Boolean(userMenuAnchor)} onClose={() => setUserMenuAnchor(null)}>
        <MenuItem disabled>
          <Box sx={{ display: "flex", flexDirection: "column" }}>
            <Typography sx={{ fontWeight: 600 }}>{session?.displayName || session?.username}</Typography>
            <Typography sx={{ color: "text.secondary", fontSize: "0.75rem" }}>{session?.username}</Typography>
          </Box>
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            setUserMenuAnchor(null);
            void logout();
          }}
        >
          <Icon name="mdi:logout" size={16} />
          <Box component="span" sx={{ ml: 1 }}>{t("logout")}</Box>
        </MenuItem>
      </Menu>

      {mobile ? (
        <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)} ModalProps={{ keepMounted: true }} sx={{ "& .MuiDrawer-paper": { width: DRAWER_WIDTH } }}>
          {drawer}
        </Drawer>
      ) : (
        <Drawer variant="permanent" sx={{ width: DRAWER_WIDTH, flexShrink: 0, "& .MuiDrawer-paper": { width: DRAWER_WIDTH, boxSizing: "border-box", borderRight: 1, borderColor: "divider" } }}>
          <Toolbar />
          {drawer}
        </Drawer>
      )}

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 4 }, pt: { xs: 10, md: 12 } }}>
        <Outlet />
      </Box>
    </Box>
  );
}
