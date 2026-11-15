import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Avatar,
  Box,
  Chip,
  CircularProgress,
  Divider,
  LinearProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { blue, green, red, yellow } from "@mui/material/colors";
import { Icon, LineChart, PageContainer, PageHeader, SectionCard } from "@pudans/components";
import { coreApiClient, type DashboardOverview } from "@pudans/api-client";

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

const SUMMARY_ROWS = [
  {
    key: "users",
    labelKey: "overviewUsers",
    icon: "pd:PeopleFilled",
    backgroundColor: blue[100],
    color: blue[600],
    value: (overview?: DashboardOverview) => overview?.totalUsers,
  },
  {
    key: "roles",
    labelKey: "overviewRolesCount",
    icon: "pd:PersonStar",
    backgroundColor: yellow[100],
    color: yellow[800],
    value: (overview?: DashboardOverview) => overview?.totalRoles,
  },
  {
    key: "groups",
    labelKey: "overviewGroups",
    icon: "pd:PeopleFilled",
    backgroundColor: green[100],
    color: green[800],
    value: (overview?: DashboardOverview) => overview?.totalUserGroups,
  },
  {
    key: "riskRules",
    labelKey: "overviewRiskRules",
    icon: "pd:ShieldLockFilled",
    backgroundColor: red[100],
    color: red[800],
    value: (overview?: DashboardOverview) => overview?.totalRiskRules,
  },
  {
    key: "webhooks",
    labelKey: "overviewWebhooks",
    icon: "pd:SendLoggingFilled",
    backgroundColor: blue[100],
    color: blue[600],
    value: (overview?: DashboardOverview) => overview?.totalWebhooks,
  },
  {
    key: "todayOperations",
    labelKey: "overviewTodayOperations",
    icon: "pd:DataHistogram",
    backgroundColor: yellow[100],
    color: yellow[800],
    value: (overview?: DashboardOverview) => overview?.todayOperations,
  },
] as const;

export function ConsoleDashboardView() {
  const { t } = useTranslation("console");
  const navigate = useNavigate();
  const [overview, setOverview] = useState<DashboardOverview>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    coreApiClient.console.dashboard
      .getDashboardOverview()
      .then((response) => {
        if (active) {
          setOverview(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setFailed(true);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const trend = (overview?.loginTrend ?? []).map((point) => ({
    date: point.date?.slice(5) ?? "",
    logins: point.count ?? 0,
  }));
  const roles = overview?.roleDistribution ?? [];
  const roleMax = Math.max(1, ...roles.map((role) => role.users ?? 0));
  const recent = overview?.recentAuditLogs ?? [];

  return (
    <PageContainer maxWidth="xl">
      <PageHeader title={t("navOverview")} />

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(12, minmax(0, 1fr))",
          gap: 3,
        }}
      >
        <Box sx={{ gridColumn: { xs: "span 12", md: "span 8", lg: "span 9" } }}>
          <SectionCard title={t("overviewTrend")}>
            {loading ? (
              <Box sx={{ height: 300, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <CircularProgress size={28} />
              </Box>
            ) : trend.length > 0 ? (
              <LineChart
                data={trend}
                xKey="date"
                series={[{ key: "logins", label: t("overviewLogins"), color: blue[600] }]}
                height={300}
              />
            ) : (
              <Box sx={{ height: 300, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Typography variant="body2" color="text.secondary">
                  {t("overviewNoTrend")}
                </Typography>
              </Box>
            )}
          </SectionCard>
        </Box>

        <Box sx={{ gridColumn: { xs: "span 12", md: "span 4", lg: "span 3" } }}>
          <SectionCard title={t("overviewSummary")}>
            {failed ? (
              <Alert severity="error">{t("overviewLoadError")}</Alert>
            ) : (
              <List disablePadding sx={{ minHeight: 300 }}>
                {SUMMARY_ROWS.map((row) => (
                  <ListItem key={row.key}>
                    <ListItemAvatar>
                      <Avatar sx={{ backgroundColor: row.backgroundColor, color: row.color }}>
                        <Icon name={row.icon} size={24} />
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      secondary={t(row.labelKey)}
                      primary={(row.value(overview) ?? 0).toLocaleString()}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </SectionCard>
        </Box>

        <Box sx={{ gridColumn: { xs: "span 12", md: "span 6" } }}>
          <SectionCard
            title={t("overviewRecent")}
            action={
              <Typography
                component="span"
                variant="body2"
                sx={{ color: "primary.main", cursor: "pointer" }}
                onClick={() => navigate("/audit")}
              >
                {t("navAudit")}
              </Typography>
            }
          >
            {loading ? (
              <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
                <CircularProgress size={24} />
              </Box>
            ) : recent.length > 0 ? (
              <List disablePadding>
                {recent.map((record, index) => (
                  <Box key={record.auditId ?? record.timestamp ?? record.operation}>
                    <ListItem sx={{ py: 1 }}>
                      <ListItemText
                        primary={record.operation ?? "-"}
                        secondary={`${record.operator ?? "-"} · ${formatTimestamp(record.timestamp)}`}
                        slotProps={{
                          primary: { variant: "body2", sx: { fontWeight: 500 }, noWrap: true },
                          secondary: { variant: "caption", noWrap: true },
                        }}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        color={record.result === "SUCCESS" ? "success" : "default"}
                        label={record.result ?? "-"}
                      />
                    </ListItem>
                    {index < recent.length - 1 ? <Divider /> : null}
                  </Box>
                ))}
              </List>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t("overviewNoRecent")}
              </Typography>
            )}
          </SectionCard>
        </Box>

        <Box sx={{ gridColumn: { xs: "span 12", md: "span 6" } }}>
          <SectionCard title={t("overviewRoles")}>
            {roles.length > 0 ? (
              <Stack spacing={2}>
                {roles.map((role) => {
                  const users = role.users ?? 0;
                  return (
                    <Box key={role.role ?? role.displayName}>
                      <Stack direction="row" sx={{ alignItems: "baseline", justifyContent: "space-between" }}>
                        <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                          {role.displayName || role.role}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {users}
                        </Typography>
                      </Stack>
                      <LinearProgress
                        variant="determinate"
                        value={(users / roleMax) * 100}
                        sx={{ mt: 1, height: 6, borderRadius: "3px" }}
                      />
                    </Box>
                  );
                })}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t("overviewNoRoles")}
              </Typography>
            )}
          </SectionCard>
        </Box>
      </Box>
    </PageContainer>
  );
}
