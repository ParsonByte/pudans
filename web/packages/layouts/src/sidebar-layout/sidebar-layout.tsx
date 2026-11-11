import {
  forwardRef,
  useState,
  type ElementType,
  type HTMLAttributes,
  type ReactNode,
} from "react";
import {
  Box,
  Drawer,
  Fade,
  IconButton,
  Stack,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { Icon } from "@pudans/components";

export interface SidebarLayoutProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  children: ReactNode;
  sidebar: ReactNode;
  sidebarHeader?: ReactNode;
  sidebarFooter?: ReactNode;
  /** Legacy `drawerOpen` inverted: `true` closes the persistent drawer. */
  collapsed?: boolean;
  defaultCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  /** Drawer width in CSS pixels; defaults to the legacy 240px (spacing 30). */
  sidebarWidth?: number;
  /**
   * @deprecated The legacy drawer has no icon-only rail; the prop is kept so
   * existing call sites keep compiling and is intentionally ignored.
   */
  collapsedWidth?: number;
  sidebarLabel?: string;
}

/**
 * Lightweight legacy shell variant: Frame/NavBar/AppDrawer + AppMain without the
 * TopAppBar. Persistent same-background drawer (toolbar-height header, Stack
 * spacing 16px, px/mx 8px) + main column whose margin animates with the drawer
 * (closed: .
 */
export const SidebarLayout = forwardRef<HTMLDivElement, SidebarLayoutProps>(function SidebarLayout(
  {
    children,
    sidebar,
    sidebarHeader,
    sidebarFooter,
    collapsed,
    defaultCollapsed = false,
    onCollapsedChange,
    sidebarWidth,
    collapsedWidth,
    sidebarLabel = "Sidebar",
    className,
    ...props
  },
  ref,
) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed);
  const [hovered, setHovered] = useState(false);

  // Deprecated prop accepted for API compatibility only.
  void collapsedWidth;

  const isCollapsed = collapsed ?? internalCollapsed;
  const drawerOpen = !isCollapsed;
  const drawerWidth =
    sidebarWidth === undefined ? theme.spacing(30) : `${sidebarWidth}px`;

  const appBarBg =
    theme.palette.mode === "light" ? theme.palette.grey[100] : theme.palette.grey[900];
  const appBarColor = theme.palette.getContrastText(appBarBg);

  const setCollapsed = (next: boolean): void => {
    if (collapsed === undefined) {
      setInternalCollapsed(next);
    }
    onCollapsedChange?.(next);
  };

  const drawerContent = (
    <>
      <Box
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        sx={{
          ...theme.mixins.toolbar,
          display: "flex",
          alignItems: "center",
          justifyContent: "flex-end",
          gap: 1,
          px: 1,
        }}
      >
        <Box sx={{ flexGrow: 1, minWidth: 0, pl: 2 }}>{sidebarHeader}</Box>
        <Fade in={isMobile || hovered}>
          <span>
            <IconButton onClick={() => setCollapsed(true)} aria-label="Collapse sidebar">
              <Icon name="mdi:chevron-left" size={theme.spacing(3)} />
            </IconButton>
          </span>
        </Fade>
      </Box>
      <Stack
        direction="column"
        spacing={2}
        sx={{ px: 1, pb: 1, mx: 1, flexGrow: 1, minHeight: 0, overflow: "auto" }}
      >
        {sidebar}
      </Stack>
      {sidebarFooter ? <Box sx={{ px: 2, pb: 2 }}>{sidebarFooter}</Box> : null}
    </>
  );

  const drawer = (
    <Drawer
      variant={isMobile ? "temporary" : "persistent"}
      anchor="left"
      open={drawerOpen}
      onClose={() => setCollapsed(true)}
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
      {drawerContent}
    </Drawer>
  );

  const MenuLink = "button" as ElementType;
  void MenuLink;

  return (
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
      {drawer}
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
            sm: drawerOpen ? 0 : `calc(${appTheme.spacing(2)} - ${drawerWidth})`,
          },
          height: "100dvh",
          minHeight: "100dvh",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
        })}
      >
        {isMobile ? (
          <Box
            sx={{
              ...theme.mixins.toolbar,
              display: "flex",
              alignItems: "center",
              px: 2,
              flexShrink: 0,
            }}
          >
            <IconButton
              color="inherit"
              aria-label="open drawer"
              edge="start"
              onClick={() => setCollapsed(false)}
            >
              <Icon name="mdi:menu" size={theme.spacing(3)} />
            </IconButton>
          </Box>
        ) : (
          <Box sx={{ ...theme.mixins.toolbar, flexShrink: 0 }} />
        )}
        <Box
          sx={{
            flexGrow: 1,
            minHeight: 0,
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
});

SidebarLayout.displayName = "SidebarLayout";
