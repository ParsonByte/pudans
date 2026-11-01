import { useId, type ReactNode } from "react";
import { Tab as MuiTab, Tabs as MuiTabs } from "@mui/material";
import { styled } from "@mui/material/styles";
import { clsx } from "clsx";
import styles from "./tabs.module.css";

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

const StyledTabs = styled(MuiTabs)(({ theme }) => ({
  minHeight: 40,
  paddingBottom: theme.spacing(1),
  "& .MuiTabs-indicator": {
    display: "none",
  },
  "& .MuiTabs-flexContainer": {
    alignItems: "center",
    gap: theme.spacing(1),
  },
  "& .MuiTabs-scrollButtons": {
    color: theme.palette.text.secondary,
  },
}));

const StyledTab = styled(MuiTab)(({ theme }) => {
  const hoverBackground =
    theme.palette.mode === "dark" ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.04)";
  return {
    minHeight: 36,
    minWidth: 0,
    padding: theme.spacing(0.75, 2),
    borderRadius: 9999,
    color: theme.palette.text.secondary,
    fontSize: 14,
    fontWeight: 500,
    lineHeight: 1.4,
    textTransform: "none",
    transition: theme.transitions.create(["background-color", "color"], {
      duration: theme.transitions.duration.shortest,
    }),
    "& .MuiTab-iconWrapper": {
      marginRight: 6,
      marginBottom: 0,
    },
    "&:hover": {
      backgroundColor: hoverBackground,
      color: theme.palette.text.primary,
    },
    "&.Mui-selected": {
      backgroundColor:
        theme.palette.mode === "dark"
          ? "rgba(144, 202, 249, 0.16)"
          : "rgba(25, 118, 210, 0.10)",
      color: theme.palette.primary.main,
    },
    "&.Mui-selected:hover": {
      backgroundColor:
        theme.palette.mode === "dark"
          ? "rgba(144, 202, 249, 0.22)"
          : "rgba(25, 118, 210, 0.16)",
    },
    "&.Mui-focusVisible": {
      boxShadow: theme.palette.mode === "dark"
        ? "0 0 0 3px rgba(144, 202, 249, 0.32)"
        : "0 0 0 3px rgba(25, 118, 210, 0.24)",
    },
    "&.Mui-disabled": {
      opacity: 0.45,
    },
  };
});

function renderLabel(item: TabItem): ReactNode {
  if (!item.badge) {
    return item.label;
  }
  return (
    <span className={styles.labelGroup}>
      <span className={styles.labelText}>{item.label}</span>
      <span className={styles.badge}>{item.badge}</span>
    </span>
  );
}

/**
 * Cloudreve-style pill tabs built on MUI Tabs/Tab. MUI supplies keyboard
 * navigation, scroll buttons and the native ripple; the styled overrides turn
 * each tab into a rounded pill with hover and selected veils, while the
 * indicator underline is hidden.
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
    <div className={clsx(styles.container, fullWidth && styles.fullWidth, className)}>
      <StyledTabs
        value={activeItem ? activeValue : false}
        onChange={(_event, nextValue) => {
          onValueChange?.(String(nextValue));
        }}
        variant={fullWidth ? "fullWidth" : "standard"}
        aria-label={ariaLabel}
        className={clsx(styles.tabs, size === "sm" && styles.tabsSm)}
      >
        {items.map((item) => (
          <StyledTab
            key={item.key}
            value={item.key}
            label={renderLabel(item)}
            icon={item.icon ? <span className={styles.icon}>{item.icon}</span> : undefined}
            iconPosition="start"
            disabled={item.disabled}
            id={`${baseId}-tab-${item.key}`}
            aria-controls={`${baseId}-panel-${item.key}`}
            className={clsx(styles.tab, size === "sm" && styles.tabSm)}
          />
        ))}
      </StyledTabs>

      {activeItem?.content !== undefined ? (
        <div
          key={activeItem.key}
          id={tabPanelId}
          role="tabpanel"
          aria-labelledby={`${baseId}-tab-${activeItem.key}`}
          className={clsx(styles.panel, panelClassName)}
        >
          {activeItem.content}
        </div>
      ) : null}
    </div>
  );
}

Tabs.displayName = "Tabs";
