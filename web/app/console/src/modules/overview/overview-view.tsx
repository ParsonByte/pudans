import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import {
  Avatar,
  Badge,
  Button,
  Icon,
  LineChart,
  PageContainer,
  PieChart,
  StatCard,
  Table,
  type BadgeTone,
  type TableColumn,
} from "@pudans/components";
import { apiClient } from "@pudans/api-client";
import { clsx } from "clsx";
import dayjs from "dayjs";
import type { TFunction } from "i18next";
import styles from "./overview-view.module.css";

const OVERVIEW_ENDPOINT =
  "/apis/console.api.pudans.tolim.cn/v1alpha1/dashboard/overview";

interface GlobalInfoSummary {
  siteTitle?: string | null;
  siteSubtitle?: string | null;
  externalUrl?: string | null;
  timezone?: string | null;
  locale?: string | null;
  version?: string | null;
  uptimeSeconds?: number;
}

interface RecentOperation {
  auditId: string | null;
  operator: string;
  operation: string | null;
  resourceType?: string | null;
  resourceName?: string | null;
  result?: string | null;
  timestamp?: string | null;
}

interface LoginTrendPoint {
  [key: string]: string | number | null | undefined;
  date: string;
  count: number;
}

interface RoleUserCount {
  [key: string]: string | number | null | undefined;
  role: string;
  displayName: string;
  users: number;
}

interface DashboardOverview {
  globalInfo?: GlobalInfoSummary | null;
  totalUsers: number;
  totalRoles: number;
  totalUserGroups: number;
  totalRiskRules: number;
  totalWebhooks: number;
  todayOperations: number;
  recentAuditLogs: RecentOperation[];
  loginTrend: LoginTrendPoint[];
  roleDistribution: RoleUserCount[];
}

type OperationResult = "SUCCESS" | "FAILURE" | "DENIED";

const RESULT_TONES: Record<OperationResult, BadgeTone> = {
  SUCCESS: "success",
  FAILURE: "danger",
  DENIED: "warning",
};

function formatUptime(seconds: number, t: TFunction<"console">): string {
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  if (days <= 0 && hours <= 0) {
    return t("uptimeLessThanHour");
  }
  if (days <= 0) {
    return t("uptimeHours", { hours });
  }
  return t("uptimeDaysHours", { days, hours });
}

export function OverviewView() {
  const { t } = useTranslation("console");
  const navigate = useNavigate();
  const [overview, setOverview] = useState<DashboardOverview | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiClient.http
      .get<DashboardOverview>(OVERVIEW_ENDPOINT, {
        signal: controller.signal,
        pudansRetry: false as const,
      })
      .then((data) => {
        setOverview(data);
      })
      .catch(() => {
        // Keep the placeholder values instead of breaking the page.
      });
    return () => {
      controller.abort();
    };
  }, []);

  const globalInfo = overview?.globalInfo ?? null;
  const operatorName = globalInfo?.siteTitle || t("overviewTitle");

  const loginTrend = overview?.loginTrend ?? [];
  const roleDistribution = overview?.roleDistribution ?? [];

  const stats = [
    {
      key: "users",
      label: t("statsUsers"),
      value: (overview?.totalUsers ?? 0).toLocaleString(),
      hint: t("statsUsersHint"),
      icon: "mdi:account-multiple-outline",
      tone: "brand" as const,
    },
    {
      key: "roles",
      label: t("statsRoles"),
      value: (overview?.totalRoles ?? 0).toLocaleString(),
      hint: t("statsRolesHint"),
      icon: "mdi:shield-account-outline",
      tone: "info" as const,
    },
    {
      key: "usergroups",
      label: t("statsUserGroups"),
      value: (overview?.totalUserGroups ?? 0).toLocaleString(),
      hint: t("statsUserGroupsHint"),
      icon: "mdi:account-group-outline",
      tone: "success" as const,
    },
    {
      key: "actions",
      label: t("statsActions"),
      value: (overview?.todayOperations ?? 0).toLocaleString(),
      hint: t("statsActionsHint"),
      icon: "mdi:history",
      tone: "warning" as const,
    },
  ];

  const operations = overview?.recentAuditLogs ?? [];

  const columns: TableColumn<RecentOperation>[] = [
    {
      key: "operator",
      header: t("colOperator"),
      width: "22%",
      render: (row) => (
        <span className={styles.operator}>
          <Avatar name={row.operator} size="sm" />
          <span>{row.operator}</span>
        </span>
      ),
    },
    {
      key: "operation",
      header: t("colAction"),
      width: "40%",
      render: (row) => (
        <span className={styles.operation}>
          <span className={styles.operationName}>{row.operation ?? "-"}</span>
          {row.resourceName ? (
            <span className={styles.operationTarget}>{row.resourceName}</span>
          ) : null}
        </span>
      ),
    },
    {
      key: "time",
      header: t("colTime"),
      width: "22%",
      render: (row) => (
        <span className={styles.time}>
          {row.timestamp ? dayjs(row.timestamp).format("YYYY-MM-DD HH:mm:ss") : "-"}
        </span>
      ),
    },
    {
      key: "status",
      header: t("colStatus"),
      width: "16%",
      align: "right",
      render: (row) => {
        const result = (row.result ?? "") as OperationResult;
        const known = result in RESULT_TONES;
        return (
          <Badge tone={known ? RESULT_TONES[result] : "neutral"} variant="soft">
            {known
              ? t(
                  result === "SUCCESS"
                    ? "statusSuccess"
                    : result === "FAILURE"
                      ? "statusFailed"
                      : "statusDenied",
                )
              : (row.result ?? "-")}
          </Badge>
        );
      },
    },
  ];

  return (
    <PageContainer title={t("overviewTitle")}>
      <div className={styles.metaLine} aria-label={operatorName}>
        <Icon name="mdi:information-outline" size={14} />
        <span>{operatorName}</span>
        {globalInfo?.version ? (
          <span className={styles.metaItem}>
            {t("metaVersion", { version: globalInfo.version })}
          </span>
        ) : null}
        {typeof globalInfo?.uptimeSeconds === "number" ? (
          <span className={styles.metaItem}>
            {t("metaUptime", { duration: formatUptime(globalInfo.uptimeSeconds, t) })}
          </span>
        ) : null}
      </div>

      <section className={styles.statsGrid} aria-label={t("overviewTitle")}>
        {stats.map((stat) => (
          <StatCard
            key={stat.key}
            label={stat.label}
            value={stat.value}
            hint={stat.hint}
            icon={<Icon name={stat.icon} size={18} />}
            tone={stat.tone}
          />
        ))}
      </section>

      <section className={styles.chartsGrid}>
        <article className={styles.panel}>
          <div className={styles.panelHeader}>
            <div>
              <h2 className={styles.panelTitle}>{t("loginChartTitle")}</h2>
              <p className={styles.panelDescription}>{t("loginChartDescription")}</p>
            </div>
            <Badge tone="brand" variant="soft">
              7D
            </Badge>
          </div>
          <LineChart
            data={loginTrend}
            xKey="date"
            series={[{ key: "count", label: t("loginSeries") }]}
            height={270}
            showDots
            emptyText={t("chartEmpty")}
          />
        </article>

        <article className={styles.panel}>
          <div className={styles.panelHeader}>
            <div>
              <h2 className={styles.panelTitle}>{t("roleChartTitle")}</h2>
              <p className={styles.panelDescription}>{t("roleChartDescription")}</p>
            </div>
          </div>
          <PieChart
            data={roleDistribution}
            nameKey="displayName"
            valueKey="users"
            height={270}
            emptyText={t("chartEmpty")}
          />
        </article>
      </section>

      <section className={clsx(styles.panel, styles.tablePanel)}>
        <div className={styles.panelHeader}>
          <div>
            <h2 className={styles.panelTitle}>{t("recentTitle")}</h2>
            <p className={styles.panelDescription}>{t("recentDescription")}</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => navigate("/audit")}>
            {t("viewAllAudit")}
            <Icon name="mdi:arrow-right" size={16} />
          </Button>
        </div>
        <Table
          variant="plain"
          columns={columns}
          data={operations}
          rowKey={(row) => row.auditId ?? String(row.timestamp ?? row.operation ?? "row")}
          hoverable
        />
      </section>
    </PageContainer>
  );
}
