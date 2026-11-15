import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Box,
  Chip,
  Stack,
  TableSortLabel,
  Typography,
} from "@mui/material";
import {
  Button,
  Icon,
  PageContainer,
  PageHeader,
  SearchInput,
  Table,
  TablePagination,
  UserAvatar,
  type TableColumn,
} from "@pudans/components";
import { coreApiClient, type ListedUser, type UserList } from "@pudans/api-client";

const DEFAULT_PAGE_SIZE = 10;

type SortField = "metadata.name" | "spec.email" | "spec.registeredAt";
type SortDirection = "asc" | "desc";

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function ConsoleUsersView() {
  const { t } = useTranslation("console");
  const [data, setData] = useState<UserList>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [orderBy, setOrderBy] = useState<SortField>("metadata.name");
  const [orderDirection, setOrderDirection] = useState<SortDirection>("asc");

  const load = useCallback(() => {
    setLoading(true);
    setFailed(false);
    coreApiClient.console.user
      .listUsers({
        page: page - 1,
        size: pageSize,
        keyword: keyword || undefined,
        sort: [`${orderBy},${orderDirection}`],
      })
      .then((response) => {
        setData(response.data);
      })
      .catch(() => {
        setFailed(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [keyword, orderBy, orderDirection, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const applyKeyword = (value: string): void => {
    setKeyword(value.trim());
    setPage(1);
  };

  const sortBy = (field: SortField): void => {
    if (orderBy === field) {
      setOrderDirection((direction) => (direction === "asc" ? "desc" : "asc"));
    } else {
      setOrderBy(field);
      setOrderDirection("asc");
    }
    setPage(1);
  };

  const sortLabel = (field: SortField, label: string) => (
    <TableSortLabel
      active={orderBy === field}
      direction={orderBy === field ? orderDirection : "asc"}
      onClick={() => sortBy(field)}
    >
      {label}
    </TableSortLabel>
  );

  const rows = data?.items ?? [];
  const total = data?.total ?? 0;

  const columns: TableColumn<ListedUser>[] = [
    {
      key: "name",
      header: sortLabel("metadata.name", t("usersColumnName")),
      width: 250,
      render: (row) => {
        const label = row.displayName || row.name || "-";
        return (
          <Stack direction="row" spacing={1} sx={{ alignItems: "center", minWidth: 0 }}>
            <UserAvatar name={label} size="sm" sx={{ flex: "0 0 auto" }} />
            <Typography variant="inherit" noWrap sx={{ fontWeight: 500, minWidth: 0 }}>
              {label}
            </Typography>
            {row.disabled ? (
              <Chip
                size="small"
                label={t("usersDisabled")}
                sx={{ borderRadius: "8px", flex: "0 0 auto" }}
              />
            ) : null}
          </Stack>
        );
      },
    },
    {
      key: "email",
      header: sortLabel("spec.email", t("usersColumnEmail")),
      width: 250,
      render: (row) => (
        <Typography
          variant="inherit"
          color={row.email ? "text.primary" : "text.secondary"}
          noWrap
        >
          {row.email || "-"}
        </Typography>
      ),
    },
    {
      key: "registeredAt",
      header: sortLabel("spec.registeredAt", t("usersColumnRegistered")),
      width: 200,
      render: (row) => (
        <Typography variant="inherit" color="text.secondary" noWrap>
          {formatTimestamp(row.registeredAt)}
        </Typography>
      ),
    },
  ];

  return (
    <PageContainer maxWidth="xl">
      <PageHeader title={t("navUsers")} />

      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ mb: 2, alignItems: { sm: "center" } }}
      >
        <SearchInput
          value={keywordInput}
          onChange={(value) => {
            setKeywordInput(value);
            if (value === "") {
              applyKeyword("");
            }
          }}
          onSearch={applyKeyword}
          placeholder={t("usersSearchPlaceholder")}
          shortcut="mod+k"
          ariaLabel={t("usersSearchPlaceholder")}
          loading={loading}
        />
        <Button
          variant="secondary"
          startIcon={<Icon name="pd:ArrowSync" size={20} />}
          disabled={loading}
          onClick={load}
        >
          {t("refresh")}
        </Button>
        <Box sx={{ flexGrow: 1 }} />
      </Stack>

      {failed ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t("usersLoadError")}
        </Alert>
      ) : null}

      <Box sx={{ mt: 2 }}>
        <Table
          columns={columns}
          data={rows}
          rowKey={(row) => row.name ?? ""}
          loading={loading}
          loadingRows={rows.length > 0 ? Math.min(rows.length, 10) : 5}
          emptyText={t("usersEmpty")}
          stickyHeader
        />
      </Box>

      {total > 0 ? (
        <TablePagination
          page={page}
          totalItems={total}
          rowsPerPage={pageSize}
          rowsPerPageOptions={[10, 25, 50, 100, 200]}
          onPageChange={setPage}
          onRowsPerPageChange={(next) => {
            setPageSize(next);
            setPage(1);
          }}
          perPageLabel={(num) => t("usersPerPage", { num })}
        />
      ) : null}
    </PageContainer>
  );
}
