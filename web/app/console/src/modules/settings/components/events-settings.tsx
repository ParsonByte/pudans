import { useTranslation } from "react-i18next";
import { Badge, Table, type TableColumn } from "@pudans/components";
import { useSystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";
import styles from "./readonly-panel.module.css";

interface EventEntry {
  plugin: string;
  points: string[];
}

export function EventsSettings() {
  const { t } = useTranslation("console");
  const { config, failed, loading } = useSystemConfig("extensionPointEnabled");

  if (failed) {
    return <ConfigFailed />;
  }
  if (loading || config === null) {
    return <SettingsLoading />;
  }

  const entries: EventEntry[] = Object.entries(config)
    .filter((entry): entry is [string, unknown[]] => Array.isArray(entry[1]))
    .map(([plugin, points]) => ({ plugin, points: points.map(String) }));

  const columns: TableColumn<EventEntry>[] = [
    {
      key: "plugin",
      header: t("settingsEventsPlugin"),
      width: "30%",
      render: (entry) => <span className={styles.primary}>{entry.plugin}</span>,
    },
    {
      key: "count",
      header: t("settingsEventsCount"),
      width: 120,
      render: (entry) => <Badge tone="brand">{entry.points.length}</Badge>,
    },
    {
      key: "points",
      header: t("settingsEventsPoints"),
      render: (entry) => (
        <span className={styles.chips}>
          {entry.points.map((point) => (
            <span key={point} className={styles.chip}>
              {point}
            </span>
          ))}
        </span>
      ),
    },
  ];

  return (
    <div className={styles.panel}>
      <p className={styles.intro}>{t("settingsEventsHelp")}</p>
      <Table
        columns={columns}
        data={entries}
        rowKey={(entry) => entry.plugin}
        emptyText={t("settingsEventsEmpty")}
      />
    </div>
  );
}
