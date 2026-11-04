import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Badge, Table, type TableColumn } from "@pudans/components";
import { apiClient } from "@pudans/api-client";
import { ConfigFailed, SettingsLoading } from "./config-state";
import styles from "./readonly-panel.module.css";

const AUTH_PROVIDERS_ENDPOINT = "/apis/auth.api.pudans.tolim.cn/v1alpha1/auth-providers";

interface ListedAuthProvider {
  name: string;
  displayName: string;
  authType?: string;
  enabled?: boolean;
  priority?: number;
}

export function ServerSettings() {
  const { t } = useTranslation("console");
  const [providers, setProviders] = useState<ListedAuthProvider[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    apiClient.http
      .get<ListedAuthProvider[]>(AUTH_PROVIDERS_ENDPOINT, {
        signal: controller.signal,
        pudansRetry: false as const,
      })
      .then((result) => {
        setProviders(Array.isArray(result) ? result : []);
      })
      .catch(() => {
        setFailed(true);
      });
    return () => {
      controller.abort();
    };
  }, []);

  if (failed) {
    return <ConfigFailed />;
  }
  if (providers === null) {
    return <SettingsLoading />;
  }

  const columns: TableColumn<ListedAuthProvider>[] = [
    {
      key: "name",
      header: t("settingsServerProvider"),
      render: (provider) => (
        <span className={styles.stack}>
          <span className={styles.primary}>{provider.displayName || provider.name}</span>
          <span className={styles.muted}>{provider.name}</span>
        </span>
      ),
    },
    {
      key: "type",
      header: t("settingsServerType"),
      width: 160,
      render: (provider) => (
        <Badge tone="info">
          {provider.authType === "FORM"
            ? t("settingsServerTypeForm")
            : t("settingsServerTypeOAuth")}
        </Badge>
      ),
    },
    {
      key: "enabled",
      header: t("settingsServerStatus"),
      width: 150,
      render: (provider) => (
        <Badge tone={provider.enabled ? "success" : "neutral"} dot>
          {provider.enabled ? t("settingsServerEnabled") : t("settingsServerDisabled")}
        </Badge>
      ),
    },
    {
      key: "priority",
      header: t("settingsServerPriority"),
      width: 120,
      align: "right",
      render: (provider) => <span className={styles.muted}>{provider.priority ?? 0}</span>,
    },
  ];

  return (
    <div className={styles.panel}>
      <p className={styles.intro}>{t("settingsServerHelp")}</p>
      <Table
        columns={columns}
        data={providers}
        rowKey={(provider) => provider.name}
        emptyText={t("settingsServerEmpty")}
      />
    </div>
  );
}
