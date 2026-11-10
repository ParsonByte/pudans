import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Box,
  Button,
  InputAdornment,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs, { type Dayjs } from "dayjs";
import Fuse from "fuse.js";
import TimeAgo from "timeago-react";
import { useQueryState } from "nuqs";
import {
  Badge,
  Icon,
  PageContainer,
  Table,
  type BadgeTone,
  type TableColumn,
} from "@pudans/components";
import { apiClient, type AuditLogPage, type AuditRecord } from "@pudans/api-client";

const AUDIT_ENDPOINT = "/apis/console.api.pudans.tolim.cn/v1alpha1/auditlogs";
const PAGE_SIZE = 50;

const RESULT_TONES: Record<string, BadgeTone> = {
  SUCCESS: "success",
  FAILURE: "danger",
  DENIED: "warning",
};

export function AuditListView() {
  const { t } = useTranslation("console");
  const [result, setResult] = useQueryState("result", { defaultValue: "ALL" });
  const [from, setFrom] = useQueryState("from", { defaultValue: "" });
  const [to, setTo] = useQueryState("to", { defaultValue: "" });
  const [items, setItems] = useState<AuditRecord[]>([]);
  const [cursor, setCursor] = useState<string | undefined>();
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [query, setQuery] = useState("");

  const loadPage = useCallback(
    async (nextCursor: string | undefined, reset: boolean): Promise<void> => {
      setLoading(true);
      setFailed(false);
      try {
        const response = await apiClient.http.get<AuditLogPage>(AUDIT_ENDPOINT, {
          params: {
            cursor: nextCursor,
            limit: PAGE_SIZE,
            result: result === "ALL" ? undefined : result,
            from: from ? dayjs(from).startOf("day").toISOString() : undefined,
            to: to ? dayjs(to).endOf("day").toISOString() : undefined,
          },
          pudansRetry: false as const,
        });
        const nextItems = Array.isArray(response.items) ? response.items : [];
        setItems((current) => (reset ? nextItems : [...current, ...nextItems]));
        setCursor(response.nextCursor ?? undefined);
        setHasMore(Boolean(response.hasMore && response.nextCursor));
      } catch {
        setFailed(true);
      } finally {
        setLoading(false);
      }
    },
    [from, result, to],
  );

  useEffect(() => {
    setItems([]);
    setCursor(undefined);
    setHasMore(false);
    void loadPage(undefined, true);
  }, [loadPage]);

  const filtered = useMemo(() => {
    if (!query.trim()) {
      return items;
    }
    return new Fuse(items, {
      keys: ["operation", "resourceType", "resourceName", "uid", "ip"],
      threshold: 0.35,
      ignoreLocation: true,
    })
      .search(query.trim())
      .map((entry) => entry.item);
  }, [items, query]);

  const columns: TableColumn<AuditRecord>[] = [
    {
      key: "operation",
      header: t("auditOperation"),
      width: "32%",
      render: (record) => (
        <span>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {record.operation || "—"}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {record.requestMethod} {record.requestPath ?? ""}
          </Typography>
        </span>
      ),
    },
    {
      key: "resource",
      header: t("auditResource"),
      width: "24%",
      render: (record) => (
        <span>
          <Typography variant="body2">{record.resourceName || "—"}</Typography>
          <Typography variant="caption" color="text.secondary">
            {record.resourceType || "—"}
          </Typography>
        </span>
      ),
    },
    {
      key: "actor",
      header: t("auditActor"),
      width: "18%",
      render: (record) => (
        <Typography variant="body2" color="text.secondary" noWrap>
          {record.uid || record.metadata.name}
        </Typography>
      ),
    },
    {
      key: "time",
      header: t("auditTime"),
      width: 160,
      render: (record) =>
        record.timestamp ? (
          <Tooltip title={dayjs(record.timestamp).format("YYYY-MM-DD HH:mm:ss")}>
            <Typography variant="body2" color="text.secondary">
              <TimeAgo datetime={record.timestamp} />
            </Typography>
          </Tooltip>
        ) : (
          "—"
        ),
    },
    {
      key: "result",
      header: t("auditResult"),
      width: 120,
      align: "right",
      render: (record) => (
        <Badge tone={RESULT_TONES[record.result ?? ""] ?? "neutral"} variant="soft">
          {record.result || "—"}
        </Badge>
      ),
    },
  ];

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <PageContainer
        title={t("auditTitle")}
        actions={
          <Button
            variant="outlined"
            size="small"
            startIcon={<Icon name="mdi:refresh" size={17} />}
            onClick={() => {
              setItems([]);
              setCursor(undefined);
              void loadPage(undefined, true);
            }}
            disabled={loading}
          >
            {t("refresh")}
          </Button>
        }
      >
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", mb: 2, flexWrap: "wrap", rowGap: 1 }}
        >
          <TextField
            size="small"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("auditSearch")}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Icon name="mdi:magnify" size={17} />
                  </InputAdornment>
                ),
              },
            }}
            sx={{ minWidth: 220 }}
          />
          <Select
            size="small"
            value={result}
            onChange={(event) => void setResult(event.target.value)}
            sx={{ minWidth: 140 }}
          >
            <MenuItem value="ALL">{t("auditAllResults")}</MenuItem>
            <MenuItem value="SUCCESS">{t("statusSuccess")}</MenuItem>
            <MenuItem value="FAILURE">{t("statusFailed")}</MenuItem>
            <MenuItem value="DENIED">{t("statusDenied")}</MenuItem>
          </Select>
          <DatePicker
            label={t("auditFrom")}
            value={from ? dayjs(from) : null}
            onChange={(value: Dayjs | null) =>
              void setFrom(value ? value.format("YYYY-MM-DD") : "")
            }
            slotProps={{ textField: { size: "small" } }}
          />
          <DatePicker
            label={t("auditTo")}
            value={to ? dayjs(to) : null}
            onChange={(value: Dayjs | null) => void setTo(value ? value.format("YYYY-MM-DD") : "")}
            slotProps={{ textField: { size: "small" } }}
          />
          <Box sx={{ flex: 1 }} />
          <Typography variant="caption" color="text.secondary">
            {t("auditLoaded", { count: filtered.length })}
          </Typography>
        </Stack>

        {failed ? (
          <Alert severity="error" sx={{ mb: 2 }}>
            {t("auditLoadFailed")}
          </Alert>
        ) : null}

        <Table
          columns={columns}
          data={filtered}
          rowKey={(record) => record.metadata.name}
          hoverable
          zebra
          emptyText={loading ? t("auditLoading") : t("auditEmpty")}
        />

        <Stack direction="row" sx={{ justifyContent: "center", mt: 2 }}>
          {hasMore ? (
            <Button
              variant="outlined"
              size="small"
              disabled={loading}
              onClick={() => void loadPage(cursor, false)}
            >
              {loading ? t("auditLoading") : t("auditLoadMore")}
            </Button>
          ) : null}
        </Stack>
      </PageContainer>
    </LocalizationProvider>
  );
}
