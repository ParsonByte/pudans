import { type ReactNode } from "react";
import { Box, Tab, Tabs } from "@mui/material";
import { PageHeaderRow, PageSurface } from "../shared/page-surface";

export interface PageTab {
  key: string;
  label: ReactNode;
}

export interface PageContainerProps {
  title: ReactNode;
  actions?: ReactNode;
  /** Accepted for API compatibility; rendered as a legacy MUI Tabs strip. */
  tabs?: readonly PageTab[];
  activeTab?: string;
  onTabChange?: (key: string) => void;
  children?: ReactNode;
  className?: string;
}

/**
 * Legacy Pages/PageContainer (Frame/RadiusFrame): the page surface card.
 * Desktop: paper + 1px divider border + 12px radius + py 32px + mb 8px.
 * Mobile (<600px): square corners, no border, no bottom margin.
 * The title row mirrors Pages/PageHeader (h4/600 + mb 32px).
 */
export function PageContainer({
  title,
  actions,
  tabs,
  activeTab,
  onTabChange,
  children,
  className,
}: PageContainerProps) {
  const currentTab = activeTab ?? tabs?.[0]?.key;
  return (
    <PageSurface className={className}>
      <PageHeaderRow title={title} actions={actions} />

      {tabs?.length ? (
        <Tabs
          value={currentTab}
          onChange={(_event, value: string) => onTabChange?.(value)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            minHeight: theme => theme.spacing(6),
            px: 3,
            borderBottom: 1,
            borderColor: "divider",
            "& .MuiTab-root": {
              minHeight: theme => theme.spacing(6),
              px: 2,
              textTransform: "none",
              fontWeight: 500,
            },
          }}
        >
          {tabs.map((tab) => (
            <Tab key={tab.key} value={tab.key} label={tab.label} />
          ))}
        </Tabs>
      ) : null}

      <Box sx={{ px: 3 }}>{children}</Box>
    </PageSurface>
  );
}

PageContainer.displayName = "PageContainer";
