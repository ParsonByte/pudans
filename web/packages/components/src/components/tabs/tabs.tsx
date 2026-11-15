import { useId, type ReactElement, type ReactNode } from "react";
import { Box, Tab as MuiTab, Tabs as MuiTabs, alpha } from "@mui/material";
import { styled } from "@mui/material/styles";

export interface TabItem {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  badge?: ReactNode;
  disabled?: boolean;
  content?: ReactNode;
}

export type TabsSize = "sm" | "md";

export interface TabsProps {
  items: readonly TabItem[];
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  size?: TabsSize;
  fullWidth?: boolean;
  className?: string;
  panelClassName?: string;
  "aria-label"?: string;
}

/**
 * Legacy Common/StyledComponents StyledTabs: 36px min-height, 24px gap between
 * tabs, no indicator bar (indicator bottom reset) and no own border.
 */
const StyledTabsBase = styled(MuiTabs)(({ theme }) => ({
  minHeight: 36,
  overflow: "initial",
  "& .MuiTabs-flexContainer": {
    gap: theme.spacing(3),
  },
  "& .MuiTabs-scroller": {
    overflow: "initial!important",
  },
  "& .MuiTabs-indicator": {
    bottom: "initial",
  },
}));

export const StyledTabs = StyledTabsBase as unknown as typeof MuiTabs;

/**
 * Legacy Common/StyledComponents StyledTab: padding 8px 0, transparent hit
 * plate drawn by ::after (8px radius, inset 4px / -8px) and a matching ripple.
 */
const StyledTabBase = styled(MuiTab)(({ theme }) => ({
  padding: `${theme.spacing(1)} 0px`,
  overflow: "initial",
  minHeight: 36,
  minWidth: 0,
  transition: theme.transitions.create(["background-color", "color"]),
  "&::after": {
    content: "''",
    borderRadius: theme.spacing(1),
    position: "absolute",
    top: theme.spacing(0.5),
    bottom: theme.spacing(0.5),
    left: "-8px",
    right: "-8px",
    pointerEvents: "none",
    transition: theme.transitions.create(["background-color"]),
  },
  "&:focus-visible::after": {
    boxShadow: `inset 0 0 0 2px ${theme.palette.primary.main}`,
  },
  "&.MuiTab-root>.MuiTab-iconWrapper": {
    height: 20,
    width: 20,
    marginRight: theme.spacing(0.5),
    marginBottom: 0,
  },
  "&.MuiTab-root": {
    flexDirection: "row",
    paddingRight: theme.spacing(0.5),
  },
  "&.MuiButtonBase-root .MuiTouchRipple-root": {
    borderRadius: theme.spacing(1),
    top: theme.spacing(0.5),
    bottom: theme.spacing(0.5),
    left: "-8px",
    right: "-8px",
    overflow: "hidden",
    pointerEvents: "none",
  },
  "&:hover": {
    "&:not(.Mui-selected)": { color: theme.palette.text.primary },
    "&::after": {
      backgroundColor: theme.palette.action.hover,
    },
    "&.Mui-selected::after": {
      backgroundColor: alpha(theme.palette.primary.main, 0.06),
    },
  },
}));

export const StyledTab = StyledTabBase as unknown as typeof MuiTab;

function renderLabel(item: TabItem): ReactElement {
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
            borderRadius: (theme) => `${theme.shape.borderRadius}px`,
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
 * MUI Tabs + legacy StyledTabs/StyledTab. Tabs keep their native keyboard
 * navigation, ripple and indicator transition; the pill/background styling of
 * the old custom tabs was removed because the legacy component had none.
 */
export function Tabs({
  items,
  value,
  defaultValue,
  onValueChange,
  size = "md",
  fullWidth = false,
  className,
  panelClassName,
  "aria-label": ariaLabel,
}: TabsProps) {
  const baseId = useId().replace(/:/g, "");
  const fallbackValue = defaultValue ?? items[0]?.key ?? "";
  const activeValue = value ?? fallbackValue;
  const activeItem = items.find((item) => item.key === activeValue);
  const tabPanelId = activeItem ? `${baseId}-panel-${activeItem.key}` : undefined;

  return (
    <Box className={className} sx={{ display: "flex", flexDirection: "column", gap: 2, minWidth: 0 }}>
      <StyledTabs
        value={activeItem ? activeValue : false}
        onChange={(_event, nextValue) => {
          onValueChange?.(String(nextValue));
        }}
        variant={fullWidth ? "fullWidth" : "standard"}
        aria-label={ariaLabel}
        sx={size === "sm" ? { minHeight: (theme) => theme.spacing(4) } : undefined}
      >
        {items.map((item) => (
          <StyledTab
            key={item.key}
            value={item.key}
            label={renderLabel(item)}
            icon={item.icon ? <>{item.icon}</> : undefined}
            iconPosition="start"
            disabled={item.disabled}
            id={`${baseId}-tab-${item.key}`}
            aria-controls={`${baseId}-panel-${item.key}`}
          />
        ))}
      </StyledTabs>

      {activeItem?.content !== undefined ? (
        <Box
          key={activeItem.key}
          id={tabPanelId}
          role="tabpanel"
          aria-labelledby={`${baseId}-tab-${activeItem.key}`}
          className={panelClassName}
          sx={{ minWidth: 0 }}
        >
          {activeItem.content}
        </Box>
      ) : null}
    </Box>
  );
}

Tabs.displayName = "Tabs";