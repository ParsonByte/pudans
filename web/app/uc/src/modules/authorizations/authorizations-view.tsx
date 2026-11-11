import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import {
  Badge,
  Icon,
  PageContainer,
  RelativeTime,
  Table,
  useToast,
  type TableColumn,
} from "@pudans/components";
import { UcPanel } from "../../components/uc-panel";

interface AuthorizationToken {
  id: string;
  name: string;
  scopes: string[];
  createdAt: string;
  lastUsedAt: string | null;
  active: boolean;
}

function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 24 * 3600 * 1000).toISOString();
}

const INITIAL_TOKENS: AuthorizationToken[] = [
  {
    id: "tok-cli",
    name: "pudans CLI",
    scopes: ["assets:read", "studio:write"],
    createdAt: isoDaysAgo(18),
    lastUsedAt: isoDaysAgo(0.2),
    active: true,
  },
  {
    id: "tok-ci",
    name: "CI publisher",
    scopes: ["assets:write"],
    createdAt: isoDaysAgo(64),
    lastUsedAt: isoDaysAgo(3),
    active: true,
  },
  {
    id: "tok-old",
    name: "Legacy dashboard",
    scopes: ["audit:read"],
    createdAt: isoDaysAgo(220),
    lastUsedAt: null,
    active: false,
  },
];

export function AuthorizationsView() {
  const { t } = useTranslation("uc");
  const toast = useToast();
  const [tokens, setTokens] = useState(INITIAL_TOKENS);

  const columns: TableColumn<AuthorizationToken>[] = useMemo(
    () => [
      {
        key: "name",
        header: t("authorizationsName"),
        width: "28%",
        render: (token) => (
          <span>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {token.name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {token.id}
            </Typography>
          </span>
        ),
      },
      {
        key: "scopes",
        header: t("authorizationsScopes"),
        width: "34%",
        render: (token) => (
          <Stack direction="row" spacing={0.75} useFlexGap sx={{ flexWrap: "wrap" }}>
            {token.scopes.map((scope) => (
              <Badge key={scope} tone="info" variant="outline">
                {scope}
              </Badge>
            ))}
          </Stack>
        ),
      },
      {
        key: "lastUsed",
        header: t("authorizationsLastUsed"),
        width: 160,
        render: (token) =>
          token.lastUsedAt ? (
            <Typography variant="body2" color="text.secondary">
              <RelativeTime datetime={token.lastUsedAt} />
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {t("authorizationsNever")}
            </Typography>
          ),
      },
      {
        key: "status",
        header: t("authorizationsStatus"),
        width: 110,
        render: (token) => (
          <Badge tone={token.active ? "success" : "neutral"} dot>
            {token.active ? t("authorizationsActive") : t("authorizationsRevoked")}
          </Badge>
        ),
      },
      {
        key: "actions",
        header: "",
        width: 100,
        align: "right",
        render: (token) => (
          <Button
            size="small"
            variant="text"
            disabled={!token.active}
            onClick={() => {
              setTokens((current) =>
                current.map((item) =>
                  item.id === token.id ? { ...item, active: false, lastUsedAt: null } : item,
                ),
              );
              toast.success(t("authorizationsRevokedToast", { name: token.name }));
            }}
          >
            {t("authorizationsRevoke")}
          </Button>
        ),
      },
    ],
    [t, toast],
  );

  return (
    <PageContainer
      title={t("authorizationsTitle")}
      actions={
        <Button
          variant="contained"
          size="small"
          startIcon={<Icon name="mdi:key-plus" size={17} />}
          onClick={() => toast.info(t("authorizationsCreateHint"))}
        >
          {t("authorizationsCreate")}
        </Button>
      }
    >
      <Alert severity="info" sx={{ mb: 2 }}>
        {t("authorizationsHelp")}
      </Alert>
      <UcPanel>
        <Table
          columns={columns}
          data={tokens}
          rowKey={(token) => token.id}
          hoverable
          zebra
          emptyText={t("authorizationsEmpty")}
        />
      </UcPanel>
      <Box sx={{ mt: 2 }}>
        <UcPanel>
          <Typography variant="body2" color="text.secondary">
            {t("authorizationsFooter")}
          </Typography>
        </UcPanel>
      </Box>
    </PageContainer>
  );
}
