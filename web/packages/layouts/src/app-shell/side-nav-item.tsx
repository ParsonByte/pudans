import { type ElementType, type ReactNode } from "react";
import { Box, ButtonBase, type ButtonBaseProps } from "@mui/material";
import { lighten, darken, styled } from "@mui/material/styles";

/**
 * Legacy Frame/NavBar/SideNavItem: ButtonBase capsule row.
 * height 32px; padding 4px; paddingLeft 28px + level*16px; radius 90px;
 * hover action.hover; active = lighten(primary, 0.7) / darken(primary, 0.7)
 * with !important; icon slot width 20px, margin-right 14px; label body2 noWrap.
 */
export interface SideNavItemProps extends Omit<ButtonBaseProps, "children"> {
  /** Leading icon (20px slot). */
  icon?: ReactNode;
  label: ReactNode;
  active?: boolean;
  /** Hide the label/trailing and center the icon (sidebars without labels). */
  collapsed?: boolean;
  trailing?: ReactNode;
  /** Element used for the row root; pass a router link to keep client routing. */
  component?: ElementType;
  /** Legacy tree indentation level: paddingLeft = 28px + level * 16px. */
  level?: number;
  /** Rendered URL when the row is an anchor (router link components receive it). */
  href?: string;
}

interface SideNavItemStyledProps {
  active?: boolean;
  level?: number;
  collapsed?: boolean;
}

const StyledItemBase = styled(ButtonBase, {
  shouldForwardProp: (prop) => prop !== "active" && prop !== "level" && prop !== "collapsed",
})<SideNavItemStyledProps>(({ theme, active, level = 0, collapsed }) => ({
  display: "flex",
  justifyContent: "flex-start",
  alignItems: "center",
  width: "100%",
  height: theme.spacing(4),
  padding: theme.spacing(0.5),
  paddingLeft: collapsed
    ? theme.spacing(0.5)
    : `calc(${theme.spacing(3.5)} + ${theme.spacing(level * 2)})`,
  borderRadius: theme.spacing(12),
  backgroundColor: active
    ? `${
        theme.palette.mode === "light"
          ? lighten(theme.palette.primary.main, 0.7)
          : darken(theme.palette.primary.main, 0.7)
      } !important`
    : "transparent",
  transition: theme.transitions.create(["background-color", "box-shadow", "border"], {
    duration: theme.transitions.duration.short,
  }),
  "&:hover": {
    backgroundColor: theme.palette.action.hover,
  },
}));

// styled(ButtonBase) drops the MUI polymorphic `component` prop from its JSX
// type, but ButtonBase supports it at runtime; expose it through ElementType.
const StyledItem: ElementType = StyledItemBase;

/**
 * Cloudreve sidebar row: a 32px transparent capsule that gains a faint hover
 * veil and a soft primary tint when active. The icon slot is a fixed 20px box
 * followed by a 14px gap and a body2 label (legacy exact values).
 */
export function SideNavItem({
  icon,
  label,
  active = false,
  collapsed = false,
  trailing,
  component,
  level = 0,
  className,
  title,
  ...rest
}: SideNavItemProps) {
  return (
    <StyledItem
      {...rest}
      component={component}
      active={active}
      level={level}
      collapsed={collapsed}
      className={className}
      title={title ?? (collapsed && typeof label === "string" ? label : undefined)}
      aria-current={active ? "page" : undefined}
    >
      <Box
        component="span"
        sx={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: theme => theme.spacing(2.5),
          mr: theme => theme.spacing(1.75),
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
      {!collapsed ? (
        <Box component="span" sx={{ flexGrow: 1, minWidth: 0 }}>
          <Box
            component="span"
            sx={{
              display: "block",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              typography: "body2",
            }}
          >
            {label}
          </Box>
        </Box>
      ) : null}
      {!collapsed && trailing ? (
        <Box
          component="span"
          sx={{ display: "inline-flex", alignItems: "center", flexShrink: 0, ml: 1 }}
        >
          {trailing}
        </Box>
      ) : null}
    </StyledItem>
  );
}

SideNavItem.displayName = "SideNavItem";