import {
  forwardRef,
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type ElementType,
  type HTMLAttributes,
  type MouseEvent,
  type ReactNode,
} from "react";
import {
  AppBar,
  Box,
  Breadcrumbs,
  Collapse,
  Divider,
  Drawer,
  Fade,
  IconButton,
  Link,
  ListItemIcon,
  ListItemText,
  MenuItem,
  MenuList,
  Popover,
  Stack,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { useHotkeys } from "react-hotkeys-hook";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import { Avatar, Icon, type DropdownMenuItem } from "@pudans/components";
import { SideNavItem } from "./side-nav-item";

export interface BreadcrumbItem {
  label: ReactNode;
  href?: string;
  onClick?: () => void;
}

export interface AppShellUser {
  name: string;
  description?: string;
  avatarSrc?: string;
  avatarIcon?: ReactNode;
}

/** One navigation entry. The shell never knows which router the app uses. */
export interface AppShellMenuItem {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  /** When present the item renders through `linkComponent` (or an <a>). */
  href?: string;
  active?: boolean;
  trailing?: ReactNode;
  title?: string;
  /** Used when the item has no href (renders a button). */
  onSelect?: () => void;
}

/** Contract for the injected router link (react-router `Link`, Next `Link`, …). */
export interface AppShellMenuLinkProps {
  href: string;
  className?: string;
  children?: ReactNode;
  title?: string;
  "aria-current"?: "page";
  onClick?: (event: MouseEvent<HTMLElement>) => void;
}

export type AppShellLinkComponent = ComponentType<AppShellMenuLinkProps>;

export interface AppShellProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  children: ReactNode;
  /** Navigation data. Provide hrefs with linkComponent to keep client routing. */
  menuItems?: readonly AppShellMenuItem[];
  /** Escape hatch for custom sidebar content rendered below the menu. */
  sidebar?: ReactNode;
  sidebarHeader?: ReactNode;
  sidebarFooter?: ReactNode;
  /** Accessible name for the navigation landmark. */
  sidebarLabel?: string;
  /** Router link adapter injected by the host application. */
  linkComponent?: AppShellLinkComponent;
  header?: ReactNode;
  breadcrumbs?: readonly BreadcrumbItem[];
  user?: AppShellUser;
  userMenuItems?: readonly DropdownMenuItem[];
  headerActions?: ReactNode;
  footer?: ReactNode;
  /**
   * Legacy persistent drawer state, inverted: `collapsed === true` renders the
   * drawer closed (off-canvas) and the AppBar full width, matching the legacy
   * `drawerOpen=false` layout. There is no icon-only rail — the legacy app
   * collapses the whole drawer.
   */
  collapsed?: boolean;
  defaultCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  /** Drawer width in CSS pixels; defaults to the legacy 240px (spacing 30). */
  sidebarWidth?: number;
  logo?: ReactNode;
  appName?: ReactNode;
  /** Click handler for the drawer brand block (usually "go home"). */
  onLogoClick?: () => void;
  enableDnd?: boolean;
  enableHotkeys?: boolean;
  onSearchShortcut?: () => void;
}

interface DrawerBodyProps {
  menuItems?: readonly AppShellMenuItem[];
  linkComponent?: AppShellLinkComponent;
  sidebarHeader?: ReactNode;
  sidebar?: ReactNode;
  sidebarFooter?: ReactNode;
  sidebarLabel: string;
  logo?: ReactNode;
  appName?: ReactNode;
  onLogoClick?: () => void;
  onCollapse?: () => void;
  showCollapseButton: boolean;
}

/**
 * Legacy Frame/NavBar/DrawerHeader: toolbar-height row, horizontal padding 8px,
 * right aligned, logo box (width 100%, pl 16px, maxWidth 160px, maxHeight 35px,
 * object-fit contain / object-position left) and a ChevronLeft button that only
 * fades in while the header is hovered.
 */
function DrawerHeaderBlock({
  logo,
  appName,
  onLogoClick,
  onCollapse,
  showCollapseButton,
}: Pick<
  DrawerBodyProps,
  "logo" | "appName" | "onLogoClick" | "onCollapse" | "showCollapseButton"
>) {
  const theme = useTheme();
  const [hovered, setHovered] = useState(false);

  return (
    <Box
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      sx={{
        ...theme.mixins.toolbar,
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        px: 1,
      }}
    >
      <Box
        onClick={onLogoClick}
        sx={{
          width: "100%",
          pl: 2,
          minWidth: 0,
          display: "flex",
          alignItems: "center",
          cursor: "pointer",
        }}
      >
        {logo ? (
          <Box
            sx={{
              width: "100%",
              maxWidth: theme.spacing(20),
              maxHeight: theme.spacing(4.375),
              display: "flex",
              alignItems: "center",
              overflow: "hidden",
              "& img": {
                width: "100%",
                height: "auto",
                maxHeight: "100%",
                objectFit: "contain",
                objectPosition: "left",
                userSelect: "none",
                WebkitUserDrag: "none",
              },
            }}
          >
            {logo}
          </Box>
        ) : appName ? (
          <Typography variant="h6" noWrap sx={{ fontWeight: 600 }}>
            {appName}
          </Typography>
        ) : null}
      </Box>
      {showCollapseButton ? (
        <Fade in={hovered}>
          <span>
            <IconButton onClick={onCollapse} aria-label="Collapse sidebar">
              <Icon name="mdi:chevron-left" size={theme.spacing(3)} />
            </IconButton>
          </span>
        </Fade>
      ) : null}
    </Box>
  );
}

/** Legacy AppDrawer content: header + scrollable Stack spacing 16px, px/mx 8px, pb 8px. */
function DrawerBody({
  menuItems,
  linkComponent,
  sidebarHeader,
  sidebar,
  sidebarFooter,
  sidebarLabel,
  logo,
  appName,
  onLogoClick,
  onCollapse,
  showCollapseButton,
}: DrawerBodyProps) {
  const MenuLink = (linkComponent ?? "a") as ElementType;

  return (
    <>
      <DrawerHeaderBlock
        logo={logo}
        appName={appName}
        onLogoClick={onLogoClick}
        onCollapse={onCollapse}
        showCollapseButton={showCollapseButton}
      />
      <Stack
        direction="column"
        spacing={2}
        sx={{ px: 1, pb: 1, mx: 1, flexGrow: 1, minHeight: 0, overflow: "auto" }}
      >
        {sidebarHeader ? <Box sx={{ width: "100%" }}>{sidebarHeader}</Box> : null}
        {menuItems?.length ? (
          <Box component="nav" aria-label={sidebarLabel} sx={{ width: "100%" }}>
            {menuItems.map((item) =>
              item.href ? (
                <SideNavItem
                  key={item.key}
                  component={MenuLink}
                  href={item.href}
                  icon={item.icon}
                  label={item.label}
                  active={item.active}
                  trailing={item.trailing}
                  title={item.title}
                  onClick={item.onSelect}
                />
              ) : (
                <SideNavItem
                  key={item.key}
                  icon={item.icon}
                  label={item.label}
                  active={item.active}
                  trailing={item.trailing}
                  title={item.title}
                  onClick={item.onSelect}
                />
              ),
            )}
          </Box>
        ) : null}
        {sidebar}
        {sidebarFooter ? <Box sx={{ width: "100%" }}>{sidebarFooter}</Box> : null}
      </Stack>
    </>
  );
}

/** Legacy Frame/NavBar/UserAction: large IconButton with a 30x30 avatar + Popover menu. */
function UserMenu({
  user,
  items,
}: {
  user: AppShellUser;
  items?: readonly DropdownMenuItem[];
}) {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);

  const button = (
    <IconButton
      size="large"
      aria-label={user.name}
      onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
    >
      <Avatar
        name={user.name}
        src={user.avatarSrc}
        icon={user.avatarIcon}
        alt={user.name}
        sx={{ width: theme.spacing(3.75), height: theme.spacing(3.75) }}
      />
    </IconButton>
  );

  if (!items?.length) {
    return button;
  }

  return (
    <>
      {button}
      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Box
          sx={{
            px: 1.5,
            py: 1.25,
            minWidth: theme.spacing(28.75),
            maxWidth: theme.spacing(37.5),
            width: "100%",
            display: "flex",
            flexDirection: "column",
            gap: 0.5,
          }}
        >
          <Typography variant="body2" noWrap sx={{ fontWeight: 600, lineHeight: 1 }}>
            {user.name}
          </Typography>
          {user.description ? (
            <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1 }}>
              {user.description}
            </Typography>
          ) : null}
        </Box>
        <Divider />
        <MenuList dense sx={{ mx: 0.5 }}>
          {items.map((item) =>
            item.divider ? (
              <Divider key={item.key} sx={{ my: 0.5 }} />
            ) : (
              <MenuItem
                key={item.key}
                disabled={item.disabled}
                onClick={() => {
                  setAnchorEl(null);
                  item.onSelect?.();
                }}
                sx={item.danger ? { color: "error.main" } : undefined}
              >
                {item.icon ? (
                  <ListItemIcon sx={{ minWidth: theme.spacing(4), color: "inherit" }}>
                    {item.icon}
                  </ListItemIcon>
                ) : null}
                <ListItemText primary={item.label} />
              </MenuItem>
            ),
          )}
        </MenuList>
      </Popover>
    </>
  );
}

/**
 * Legacy application shell (Frame/NavBarFrame):
 *
 *   fixed MUI AppBar (elevation 0, grey[100]/grey[900] background, contrast text)
 *   persistent left Drawer (same background, no right border, toolbar-height
 *   logo header, Stack spacing 16px of navigation)
 *   flex main column with margin transitions, toolbar spacer and the page
 *   content (page cards are owned by PageContainer/PageLayout).
 *
 * Below the sm breakpoint (<600px) the persistent drawer is replaced by a 70vw
 * Popover anchored to the AppBar menu button, exactly like the legacy
 * DrawerPopover. `collapsed` is the legacy `drawerOpen` flag inverted.
 */
export const AppShell = forwardRef<HTMLDivElement, AppShellProps>(function AppShell(
  {
    children,
    menuItems,
    sidebar,
    sidebarHeader,
    sidebarFooter,
    sidebarLabel = "Sidebar",
    linkComponent,
    header,
    breadcrumbs,
    user,
    userMenuItems,
    headerActions,
    footer,
    collapsed,
    defaultCollapsed = false,
    onCollapsedChange,
    sidebarWidth,
    logo,
    appName,
    onLogoClick,
    enableDnd = true,
    enableHotkeys = true,
    onSearchShortcut,
    className,
    ...props
  },
  ref,
) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [menuAnchorEl, setMenuAnchorEl] = useState<HTMLElement | null>(null);
  const menuButtonRef = useRef<HTMLButtonElement | null>(null);

  const isCollapsed = collapsed ?? internalCollapsed;
  const drawerOpen = !isCollapsed;
  const drawerWidth =
    sidebarWidth === undefined ? theme.spacing(30) : `${sidebarWidth}px`;

  const appBarBg =
    theme.palette.mode === "light" ? theme.palette.grey[100] : theme.palette.grey[900];
  const appBarColor = theme.palette.getContrastText(appBarBg);

  useEffect(() => {
    if (!isMobile) {
      setMobileOpen(false);
      setMenuAnchorEl(null);
    }
  }, [isMobile]);

  const setCollapsed = (next: boolean): void => {
    if (collapsed === undefined) {
      setInternalCollapsed(next);
    }
    onCollapsedChange?.(next);
  };

  const handleDrawerCollapse = (): void => {
    setCollapsed(true);
    setMobileOpen(false);
  };

  const handleMenuClick = (event: MouseEvent<HTMLElement>): void => {
    if (isMobile) {
      setMenuAnchorEl(event.currentTarget);
      setMobileOpen(true);
      return;
    }
    setCollapsed(false);
  };

  useHotkeys(
    "mod+b",
    (event) => {
      event.preventDefault();
      if (isMobile) {
        setMenuAnchorEl(menuButtonRef.current);
        setMobileOpen((open) => !open);
        return;
      }
      setCollapsed(!isCollapsed);
    },
    { enabled: enableHotkeys, preventDefault: true },
    [enableHotkeys, isCollapsed, isMobile, collapsed],
  );

  useHotkeys(
    "mod+k",
    (event) => {
      event.preventDefault();
      onSearchShortcut?.();
    },
    { enabled: enableHotkeys && Boolean(onSearchShortcut), preventDefault: true },
    [enableHotkeys, onSearchShortcut],
  );

  const userButton = user ? <UserMenu user={user} items={userMenuItems} /> : null;

  const drawerBody = (showCollapseButton: boolean, onCollapse?: () => void): ReactNode => (
    <DrawerBody
      menuItems={menuItems}
      linkComponent={linkComponent}
      sidebarHeader={sidebarHeader}
      sidebar={sidebar}
      sidebarFooter={sidebarFooter}
      sidebarLabel={sidebarLabel}
      logo={logo}
      appName={appName}
      onLogoClick={onLogoClick}
      onCollapse={onCollapse}
      showCollapseButton={showCollapseButton}
    />
  );

  const breadcrumbRow =
    !isMobile && breadcrumbs?.length ? (
      <Breadcrumbs
        aria-label="Breadcrumb"
        separator="/"
        sx={{ ml: 1, minWidth: 0, "& .MuiBreadcrumbs-ol": { flexWrap: "nowrap" } }}
      >
        {breadcrumbs.map((item, index) => {
          const isLast = index === breadcrumbs.length - 1;
          if (isLast) {
            return (
              <Typography
                key={index}
                variant="body2"
                color="text.primary"
                aria-current="page"
                noWrap
              >
                {item.label}
              </Typography>
            );
          }
          return (
            <Link
              key={index}
              component={item.href ? "a" : "button"}
              {...(item.href ? { href: item.href } : { type: "button" })}
              variant="body2"
              color="text.secondary"
              underline="hover"
              onClick={item.onClick}
              sx={{ cursor: "pointer", maxWidth: theme.spacing(22.5) }}
              noWrap
            >
              {item.label}
            </Link>
          );
        })}
      </Breadcrumbs>
    ) : null;

  const defaultToolbar = (
    <>
      <Collapse orientation="horizontal" in={isCollapsed || isMobile}>
        <IconButton
          ref={menuButtonRef}
          color="inherit"
          aria-label="open drawer"
          onClick={handleMenuClick}
          edge="start"
          sx={{ mr: isMobile ? 1 : 2, ml: isMobile ? -1 : -1.5 }}
        >
          <Icon name="mdi:menu" size={theme.spacing(3)} />
        </IconButton>
      </Collapse>
      {breadcrumbRow}
      <Box sx={{ flexGrow: 1 }} />
      <Stack direction="row" sx={{ alignItems: "center" }} spacing={isMobile ? 1 : 0}>
        {headerActions}
        {userButton}
      </Stack>
    </>
  );

  const shell = (
    <Box
      ref={ref}
      className={className}
      sx={{
        display: "flex",
        width: "100%",
        height: "100dvh",
        minHeight: "100dvh",
        overflow: "hidden",
        backgroundColor: appBarBg,
        color: appBarColor,
      }}
      {...props}
    >
      <Box
        component="a"
        href="#pd-main"
        sx={{
          position: "fixed",
          top: 0,
          left: 0,
          zIndex: "tooltip",
          transform: "translateY(-150%)",
          "&:focus": { transform: "translateY(0)" },
          px: 2,
          py: 1,
          borderRadius: 1,
          backgroundColor: "background.paper",
          color: "text.primary",
          textDecoration: "none",
        }}
      >
        Skip to content
      </Box>

      {!isMobile ? (
        <Drawer
          variant="persistent"
          anchor="left"
          open={drawerOpen}
          sx={{
            width: drawerWidth,
            flexShrink: 0,
            display: "flex",
            "& .MuiDrawer-paper": {
              width: drawerWidth,
              boxSizing: "border-box",
              backgroundColor: appBarBg,
              color: appBarColor,
              borderRight: "none",
            },
          }}
        >
          {drawerBody(true, handleDrawerCollapse)}
        </Drawer>
      ) : (
        <Popover
          open={mobileOpen}
          anchorEl={menuAnchorEl}
          onClose={() => setMobileOpen(false)}
          anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
          transformOrigin={{ vertical: "top", horizontal: "left" }}
          slotProps={{
            paper: {
              sx: {
                width: "70vw",
                mt: 1,
                backgroundColor: appBarBg,
                color: appBarColor,
                backgroundImage: "none",
              },
            },
          }}
        >
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              height: "70dvh",
              minHeight: 0,
            }}
          >
            {drawerBody(false)}
          </Box>
        </Popover>
      )}

      <AppBar
        position="fixed"
        elevation={0}
        sx={(appTheme) => ({
          transition: appTheme.transitions.create(["margin", "width"], {
            easing: appTheme.transitions.easing.sharp,
            duration: appTheme.transitions.duration.leavingScreen,
          }),
          backgroundColor: appBarBg,
          color: appBarColor,
          ...(drawerOpen && !isMobile
            ? {
                width: `calc(100% - ${drawerWidth})`,
                marginLeft: drawerWidth,
                transition: appTheme.transitions.create(["margin", "width"], {
                  easing: appTheme.transitions.easing.easeOut,
                  duration: appTheme.transitions.duration.enteringScreen,
                }),
              }
            : {}),
        })}
      >
        <Toolbar
          sx={(appTheme) => ({
            "&.MuiToolbar-root.MuiToolbar-gutters": {
              paddingLeft: drawerOpen && !isMobile ? 0 : appTheme.spacing(isMobile ? 2 : 3),
              transition: appTheme.transitions.create("padding", {
                easing: appTheme.transitions.easing.easeInOut,
                duration: appTheme.transitions.duration.standard,
              }),
            },
          })}
        >
          {header ?? defaultToolbar}
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={(appTheme) => ({
          flexGrow: 1,
          transition: appTheme.transitions.create("margin", {
            easing: drawerOpen
              ? appTheme.transitions.easing.easeOut
              : appTheme.transitions.easing.sharp,
            duration: drawerOpen
              ? appTheme.transitions.duration.enteringScreen
              : appTheme.transitions.duration.leavingScreen,
          }),
          marginRight: { xs: 0, sm: appTheme.spacing(2) },
          marginLeft: {
            xs: 0,
            sm: drawerOpen
              ? 0
              : `calc(${appTheme.spacing(2)} - ${drawerWidth})`,
          },
          height: "100dvh",
          minHeight: "100dvh",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
        })}
      >
        <Box sx={{ ...theme.mixins.toolbar, display: "flex", justifyContent: "flex-end" }} />
        <Box
          id="pd-main"
          tabIndex={-1}
          sx={{
            flexGrow: 1,
            minHeight: 0,
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            outline: "none",
          }}
        >
          {children}
        </Box>
        {footer ? (
          <Box sx={{ flexShrink: 0, px: 3, py: 1.5, color: appBarColor }}>{footer}</Box>
        ) : null}
      </Box>
    </Box>
  );

  return enableDnd ? <DndProvider backend={HTML5Backend}>{shell}</DndProvider> : shell;
});

AppShell.displayName = "AppShell";