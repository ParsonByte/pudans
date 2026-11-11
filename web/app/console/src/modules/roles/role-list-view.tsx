import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Box, InputAdornment, Stack, TextField, Typography } from "@mui/material";
import Fuse from "fuse.js";
import { Badge, HighlightText, Icon, Table, type TableColumn, RelativeTime } from "@pudans/components";
import { PageContainer } from "@pudans/layouts";
import { apiClient, type Role, type RoleList } from "@pudans/api-client";

const ROLES_ENDPOINT = "/apis/console.api.pudans.tolim.cn/v1alpha1/roles";
const DISPLAY_NAME = "rbac.authorization.pudans.tolim.cn/display-name";
const TEMPLATE_LABEL = "pudans.tolim.cn/role-template";
const HIDDEN_LABEL = "pudans.tolim.cn/hidden";

function displayName(role: Role): string {
  return role.metadata.annotations?.[DISPLAY_NAME] || role.metadata.name;
}

export function RoleListView() {
  const { t } = useTranslation("console");
  const [roles, setRoles] = useState<Role[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    apiClient.http
      .get<RoleList>(ROLES_ENDPOINT, {
        signal: controller.signal,
        params: { page: 0, size: 200 },
        pudansRetry: false as const,
      })
      .then((result) => setRoles(Array.isArray(result.items) ? result.items : []))
      .catch(() => {
        setFailed(true);
        setRoles([]);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, []);

  const filtered = useMemo(() => {
    if (!query.trim()) {
      return roles;
    }
    return new Fuse(roles, {
      keys: ["metadata.name", "metadata.annotations"],
      threshold: 0.3,
      ignoreLocation: true,
    })
      .search(query.trim())
      .map((result) => result.item);
  }, [query, roles]);

  const columns: TableColumn<Role>[] = [
    {
      key: "role",
      header: t("rolesName"),
      width: "36%",
      render: (role) => (
        <span style={{ minWidth: 0 }}>
          <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
            <HighlightText
              text={displayName(role)}
              searchWords={query.trim() ? [query.trim()] : []}
            />
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap>
            {role.metadata.name}
          </Typography>
        </span>
      ),
    },
    {
      key: "rules",
      header: t("rolesRules"),
      width: 110,
      render: (role) => <Badge tone="brand">{role.rules?.length ?? 0}</Badge>,
    },
    {
      key: "kind",
      header: t("rolesType"),
      width: 180,
      render: (role) => {
        const template = role.metadata.labels?.[TEMPLATE_LABEL] === "true";
        const hidden = role.metadata.labels?.[HIDDEN_LABEL] === "true";
        return (
          <Stack direction="row" spacing={0.75}>
            <Badge tone={template ? "warning" : "info"} variant="outline">
              {template ? t("rolesTemplate") : t("rolesCustom")}
            </Badge>
            {hidden ? <Badge tone="neutral">{t("rolesHidden")}</Badge> : null}
          </Stack>
        );
      },
    },
    {
      key: "created",
      header: t("rolesCreated"),
      width: 160,
      render: (role) =>
        role.metadata.creationTimestamp ? (
          <Typography variant="body2" color="text.secondary">
            <RelativeTime datetime={role.metadata.creationTimestamp} />
          </Typography>
        ) : (
          "—"
        ),
    },
  ];

  return (
    <PageContainer title={t("rolesTitle")}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("rolesSearch")}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Icon name="mdi:magnify" size={17} />
                </InputAdornment>
              ),
            },
          }}
          sx={{ minWidth: 260 }}
        />
        <Box sx={{ flex: 1 }} />
        <Typography variant="caption" color="text.secondary">
          {t("rolesTotal", { total: roles.length })}
        </Typography>
      </Stack>
      {failed ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t("rolesLoadFailed")}
        </Alert>
      ) : null}
      <Table
        columns={columns}
        data={filtered}
        rowKey={(role) => role.metadata.name}
        hoverable
        zebra
        loading={loading}

        emptyText={t("rolesEmpty")}
      />
    </PageContainer>
  );
}
