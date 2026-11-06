import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon, PageContainer, Tabs, type TabItem } from "@pudans/components";
import { SiteSettings } from "./components/site-settings";
import { UserSettings } from "./components/user-settings";
import { CaptchaSettings } from "./components/captcha-settings";
import { MailSettings } from "./components/mail-settings";
import { QueueSettings } from "./components/queue-settings";
import { AppearanceSettings } from "./components/appearance-settings";
import { EventsSettings } from "./components/events-settings";
import { ServerSettings } from "./components/server-settings";

export function SettingsView() {
  const { t } = useTranslation("console");
  const [activeTab, setActiveTab] = useState("site");

  const tabItems: TabItem[] = [
    {
      key: "site",
      label: t("settingsTabSite"),
      icon: <Icon name="mdi:web" size={17} />,
      content: <SiteSettings />,
    },
    {
      key: "session",
      label: t("settingsTabSession"),
      icon: <Icon name="mdi:account-clock-outline" size={17} />,
      content: <UserSettings />,
    },
    {
      key: "captcha",
      label: t("settingsTabCaptcha"),
      icon: <Icon name="mdi:shield-check-outline" size={17} />,
      content: <CaptchaSettings />,
    },
    {
      key: "mail",
      label: t("settingsTabMail"),
      icon: <Icon name="mdi:email-outline" size={17} />,
      content: <MailSettings />,
    },
    {
      key: "queue",
      label: t("settingsTabQueue"),
      icon: <Icon name="mdi:stack-outline" size={17} />,
      content: <QueueSettings />,
    },
    {
      key: "appearance",
      label: t("settingsTabAppearance"),
      icon: <Icon name="mdi:palette-outline" size={17} />,
      content: <AppearanceSettings />,
    },
    {
      key: "events",
      label: t("settingsTabEvents"),
      icon: <Icon name="mdi:bell-outline" size={17} />,
      content: <EventsSettings />,
    },
    {
      key: "server",
      label: t("settingsTabServer"),
      icon: <Icon name="mdi:server-outline" size={17} />,
      content: <ServerSettings />,
    },
  ];

  return (
    <PageContainer title={t("settingsTitle")}>
      <Tabs
        items={tabItems}
        value={activeTab}
        onValueChange={setActiveTab}
        aria-label={t("settingsTitle")}
      />
    </PageContainer>
  );
}
