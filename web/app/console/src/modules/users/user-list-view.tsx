import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Box, Button, InputAdornment, Stack, TextField, Typography } from "@mui/material";
import Fuse from "fuse.js";
import { Avatar, Badge, HighlightText, Icon, Pagination, Table, type TableColumn, RelativeTime } from "@pudans/components";
import { PageContainer } from "@pudans/layouts";
import { apiClient, type ListedUser, type UserList } from "@pudans/api-client";

const USERS_ENDPOINT = "/apis/console.api.pudans.tolim.cn/v1alpha1/users";
const PAGE_SIZE = 20;

export function UserListView() {
  const { t } = useTranslation("console");
  const [users, setUsers] = useState<ListedUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setFailed(false);
    apiClient.http
      .get<UserList>(USERS_ENDPOINT, {
        signal: controller.signal,
        params: {
          page: page - 1,
          size: PAGE_SIZE,
          sort: ["metadata.creationTimestamp,desc"],
        },
        pudansRetry: false as const,
      })
      .then((result) => {
        setUsers(Array.isArray(result.items) ? result.items : []);
        setTotal(result.total ?? 0);
      })
      .catch(() => {
        setFailed(true);
        setUsers([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [page]);

  const filtered = useMemo(() => {
    if (!query.trim()) {
      return users;
    }
    return new Fuse(users, {
      keys: ["displayName", "name", "email"],
      threshold: 0.35,
      ignoreLocation: true,
    })
      .search(query.trim())
      .map((result) => result.item);
  }, [query, users]);

  const columns: TableColumn<ListedUser>[] = [
    {
      key: "name",
      header: t("usersName"),
      width: "30%",
      render: (user) => (
        <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Avatar name={user.displayName || user.name || "?"} size="sm" />
          <span style={{ minWidth: 0 }}>
            <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
              <HighlightText
                text={user.displayName || user.name || "-"}
                searchWords={query.trim() ? [query.trim()] : []}
              />
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              <HighlightText
                text={user.name ?? "-"}
                searchWords={query.trim() ? [query.trim()] : []}
              />
            </Typography>
          </span>
        </span>
      ),
    },
    {
      key: "email",
      header: t("usersEmail"),
      width: "28%",
      render: (user) => (
        <Typography variant="body2" color="text.secondary">
          {user.email || "—"}
        </Typography>
      ),
    },
    {
      key: "status",
      header: t("usersStatus"),
      width: 120,
      render: (user) => (
        <Badge tone={user.disabled ? "danger" : "success"} dot>
          {user.disabled ? t("usersDisabled") : t("usersEnabled")}
        </Badge>
      ),
    },
    {
      key: "registered",
      header: t("usersRegistered"),
      width: 160,
      render: (user) =>
        user.registeredAt ? (
          <Typography variant="body2" color="text.secondary">
            <RelativeTime datetime={user.registeredAt} locale={undefined} />
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary">
            —
          </Typography>
        ),
    },
  ];

  return (
    <PageContainer
      title={t("usersTitle")}
      actions={
        <Button
          variant="contained"
          size="small"
          startIcon={<Icon name="mdi:account-plus-outline" size={17} />}
        >
          {t("usersInvite")}
        </Button>
      }
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("usersSearch")}
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
          {t("usersTotal", { total })}
        </Typography>
      </Stack>

      {failed ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t("usersLoadFailed")}
        </Alert>
      ) : null}

      <Table
        columns={columns}
        data={filtered}
        rowKey={(user) => user.name ?? user.email ?? ""}
        hoverable
        zebra
        loading={loading}

        emptyText={t("usersEmpty")}
      />

      <Stack direction="row" sx={{ justifyContent: "flex-end", mt: 2 }}>
        <Pagination page={page} total={total} pageSize={PAGE_SIZE} onPageChange={setPage} />
      </Stack>
    </PageContainer>
  );
}
