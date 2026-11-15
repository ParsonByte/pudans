import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import { Box } from "@mui/material";
import { Icon, PageContainer, PageHeader, ResponsiveTabs } from "@pudans/components";

type SettingsTab = "profile" | "security" | "authorizations";

function currentTab(pathname: string): SettingsTab {
  if (pathname.startsWith("/security")) {
    return "security";
  }
  if (pathname.startsWith("/authorizations")) {
    return "authorizations";
  }
  return "profile";
}

/**
 * User-centre settings shell copied from the reference Pages/Setting/Setting:
 * PageContainer at maxWidth lg, a single "Account settings" PageHeader, then
 * the divider-bottomed ResponsiveTabs. The three reference tabs map onto the
 * three UC routes so the sidebar and the tab bar stay in sync.
 */
export function UcSettingsPage({ children }: { children: ReactNode }) {
  const { t } = useTranslation("uc");
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <PageContainer maxWidth="lg">
      <PageHeader title={t("accountSettings")} />
      <ResponsiveTabs
        value={currentTab(location.pathname)}
        onChange={(next) => navigate(`/${next}`)}
        moreLabel={t("showMore")}
        ariaLabel={t("accountSettings")}
        items={[
          {
            key: "profile",
            label: t("navProfile"),
            icon: <Icon name="mdi:account-circle-outline" size={20} />,
          },
          {
            key: "security",
            label: t("navSecurity"),
            icon: <Icon name="mdi:shield-lock-outline" size={20} />,
          },
          {
            key: "authorizations",
            label: t("navAuthorizations"),
            icon: <Icon name="mdi:key-variant" size={20} />,
          },
        ]}
      />
      <Box sx={{ mt: 3 }}>{children}</Box>
    </PageContainer>
  );
}
