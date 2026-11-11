import { useLayoutEffect, useRef, useState, type ReactElement, type ReactNode } from "react";
import {
  Box,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { bindMenu, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import { Icon } from "../icon";
import { StyledTab, StyledTabs } from "../tabs";

export interface ResponsiveTabItem<T extends string = string> {
  key: T;
  label: ReactNode;
  icon?: ReactNode;
  badge?: ReactNode;
}

export interface ResponsiveTabsProps<T extends string = string> {
  items: readonly ResponsiveTabItem<T>[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel?: string;
  className?: string;
  /** Label of the overflow trigger tab (legacy i18n "showMore"). */
  moreLabel?: ReactNode;
}

function renderLabel(item: ResponsiveTabItem): ReactElement {
  return (
    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.5 }}>
      <Box component="span">{item.label}</Box>
      {item.badge ? (
        <Box
          component="span"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            px: 0.75,
            borderRadius: (theme) => theme.shape.borderRadius,
            backgroundColor: "action.selected",
            color: "primary.main",
            fontSize: (theme) => theme.typography.caption.fontSize,
            lineHeight: (theme) => theme.typography.caption.lineHeight,
          }}
        >
          {item.badge}
        </Box>
      ) : null}
    </Box>
  );
}

/**
 * Legacy Common/ResponsiveTabs: a divider-bottomed 2px padded box containing
 * the legacy StyledTabs. Below the sm breakpoint or when the tab row overflows,
 * only the active tab plus a "Show more" tab are rendered; the remaining tabs
 * move into a MUI Menu.
 */
export function ResponsiveTabs<T extends string = string>({
  items,
  value,
  onChange,
  ariaLabel = "Tabs",
  className,
  moreLabel = "Show more",
}: ResponsiveTabsProps<T>) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const tabsRef = useRef<HTMLDivElement>(null);
  const [hideTabs, setHideTabs] = useState(false);
  const moreOptionState = usePopupState({ variant: "popover", popupId: "pd-tabs-more" });
  const { onClose, ...menuProps } = bindMenu(moreOptionState);

  useLayoutEffect(() => {
    const checkOverflow = (): void => {
      const flex = tabsRef.current?.querySelector<HTMLElement>(".MuiTabs-flexContainer");
      if (!flex) {
        return;
      }
      setHideTabs((previous) => (previous ? true : flex.scrollWidth > flex.clientWidth));
    };
    checkOverflow();
    window.addEventListener("resize", checkOverflow);
    return () => window.removeEventListener("resize", checkOverflow);
  }, [items.length]);

  const collapsed = isMobile || hideTabs;
  const visibleItems = collapsed ? items.filter((item) => item.key === value) : items;
  const otherItems = items.filter((item) => item.key !== value);

  return (
    <Box className={className} sx={{ borderBottom: 1, borderColor: "divider", pb: 0.25 }}>
      <StyledTabs
        ref={tabsRef}
        value={value}
        onChange={(_event, nextValue) => onChange(nextValue as T)}
        aria-label={ariaLabel}
      >
        {visibleItems.map((item) => (
          <StyledTab
            key={item.key}
            value={item.key}
            label={renderLabel(item)}
            icon={item.icon ? <>{item.icon}</> : undefined}
          />
        ))}
        {collapsed && items.length > 1 ? (
          <>
            <StyledTab
              label={
                <Box component="span" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  {moreLabel}
                  <Icon name="cr:CaretDown" size={15} />
                </Box>
              }
              {...bindTrigger(moreOptionState)}
            />
            <Menu
              {...menuProps}
              onClose={() => {
                moreOptionState.close();
                onClose?.();
              }}
            >
              {otherItems.map((item) => (
                <MenuItem
                  key={item.key}
                  onClick={() => {
                    moreOptionState.close();
                    onChange(item.key);
                  }}
                >
                  {item.icon ? <ListItemIcon>{item.icon}</ListItemIcon> : null}
                  <ListItemText>{item.label}</ListItemText>
                </MenuItem>
              ))}
            </Menu>
          </>
        ) : null}
      </StyledTabs>
    </Box>
  );
}