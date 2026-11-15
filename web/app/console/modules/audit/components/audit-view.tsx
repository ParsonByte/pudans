import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Box,
  Button as MuiButton,
  Chip,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { Button, Icon, PageContainer, PageHeader, Table, type TableColumn } from "@pudans/components";
import { coreApiClient, type AuditLogPage, type AuditRecord } from "@pudans/api-client";

const DEFAULT_PAGE_SIZE = 10;

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function resultColor(result?: string): "success" | "error" | "warning" | "default" {
  switch (result) {
    case "SUCCESS":
      return "success";
    case "FAILURE":
      return "error";
    case "DENIED":
      return "warning";
    default:
      return "default";
  }
}

export function ConsoleAuditView() {
  const { t } = useTranslation("console");
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const [data, setData] = useState<AuditLogPage>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
  const [cursors, setCursors] = useState<Array<string | undefined>>([undefined]);
  const [pageIndex, setPageIndex] = useState(0);

  const cursor = cursors[pageIndex];

  const load = useCallback(() => {
    setLoading(true);
    setFailed(false);
    coreApiClient.console.auditLog
      .listAuditLogs({ cursor, limit, order: "desc" })
      .then((response) => {
        setData(response.data);
      })
      .catch(() => {
        setFailed(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [cursor, limit]);

  useEffect(() => {
    load();
  }, [load]);

  const hasNext = data?.hasMore === true && Boolean(data?.nextCursor);

  const goNext = (): void => {
    if (!data?.nextCursor) {
      return;
    }
    const nextCursor = data.nextCursor;
    setCursors((previous) => {
      const next = previous.slice(0, pageIndex + 1);
      next[pageIndex + 1] = nextCursor;
      return next;
    });
    setPageIndex((index) => index + 1);
  };

  const goPrevious = (): void => {
    setPageIndex((index) => Math.max(0, index - 1));
  };

  const rows = data?.items ?? [];

  const columns: TableColumn<AuditRecord>[] = [
    {
      key: "timestamp",
      header: t("auditColumnTime"),
      width: 180,
      render: (row) => (
        <Typography variant="inherit" color="text.secondary" noWrap>
          {formatTimestamp(row.timestamp)}
        </Typography>
      ),
    },
    {
      key: "operation",
      header: t("auditColumnOperation"),
      width: 220,
      render: (row) => (
        <Typography variant="inherit" noWrap sx={{ fontWeight: 500 }}>
          {row.operation || "-"}
        </Typography>
      ),
    },
    {
      key: "resource",
      header: t("auditColumnResource"),
      width: 150,
      render: (row) => (
        <Typography variant="inherit" color="text.secondary" noWrap>
          {row.resourceType
            ? `${row.resourceType}${row.resourceName ? ` · ${row.resourceName}` : ""}`
            : "-"}
        </Typography>
      ),
    },
    {
      key: "operator",
      header: t("auditColumnOperator"),
      width: 150,
      render: (row) => (
        <Typography variant="inherit" color="text.secondary" noWrap>
          {row.uid || "-"}
        </Typography>
      ),
    },
    {
      key: "request",
      header: t("auditColumnRequest"),
      render: (row) => (
        <Typography variant="inherit" color="text.secondary" noWrap>
          {[row.requestMethod, row.requestPath].filter(Boolean).join(" ") || "-"}
        </Typography>
      ),
    },
    {
      key: "result",
      header: t("auditColumnResult"),
      width: 110,
      render: (row) => (
        <Chip
          size="small"
          color={resultColor(row.result)}
          label={row.result || "-"}
          sx={{ borderRadius: "8px" }}
        />
      ),
    },
  ];

  return (
    <PageContainer maxWidth="xl">
      <PageHeader title={t("navAudit")} />

      <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: "center" }}>
        <Button
          variant="secondary"
          startIcon={<Icon name="pd:ArrowSync" size={20} />}
          disabled={loading}
          onClick={load}
        >
          {t("refresh")}
        </Button>
      </Stack>

      {failed ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t("auditLoadError")}
        </Alert>
      ) : null}

      <Box sx={{ mt: 2 }}>
        <Table
          columns={columns}
          data={rows}
          rowKey={(row) => row.metadata?.name ?? row.traceId ?? ""}
          loading={loading}
          loadingRows={rows.length > 0 ? Math.min(rows.length, 10) : 5}
          emptyText={t("auditEmpty")}
          stickyHeader
        />
      </Box>

      <Box
        sx={{
          py: 1,
          display: "flex",
          flexDirection: isMobile ? "column" : "row",
          justifyContent: "space-between",
          alignItems: isMobile ? "flex-start" : "center",
          gap: 1,
        }}
      >
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <MuiButton
            variant="outlined"
            size="small"
            disabled={pageIndex === 0 || loading}
            onClick={goPrevious}
            startIcon={<Icon name="mdi:chevron-left" size={20} />}
          >
            {t("auditPrevious")}
          </MuiButton>
          <Typography variant="body2" color="text.secondary">
            {t("auditPage", { page: pageIndex + 1 })}
          </Typography>
          <MuiButton
            variant="outlined"
            size="small"
            disabled={!hasNext || loading}
            onClick={goNext}
            endIcon={<Icon name="mdi:chevron-right" size={20} />}
          >
            {t("auditNext")}
          </MuiButton>
        </Stack>
        <Select<number>
          variant="filled"
          size="small"
          value={limit}
          onChange={(event) => {
            setLimit(Number(event.target.value));
            setCursors([undefined]);
            setPageIndex(0);
          }}
          sx={{
            minHeight: 39,
            "& .MuiFilledInput-input": {
              display: "flex",
              alignItems: "center",
              paddingTop: "4px",
              paddingBottom: "4px",
            },
          }}
          renderValue={(value) => (
            <ListItemText
              primary={t("auditPerPage", { num: Number(value) })}
              slotProps={{ primary: { variant: "body2" } }}
            />
          )}
        >
          {[10, 25, 50, 100, 200].map((option) => (
            <MenuItem key={option} value={option}>
              <ListItemText
                primary={t("auditPerPage", { num: option })}
                slotProps={{ primary: { variant: "body2" } }}
              />
            </MenuItem>
          ))}
        </Select>
      </Box>
    </PageContainer>
  );
}
