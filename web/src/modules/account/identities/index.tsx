import { useEffect, useRef, useState } from "react";

import { type UserConnection, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Link2, Loader2, Plus, Unlink } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

const CONNECTIONS_QUERY_KEY = ["account-connections"];
const OIDC_PENDING_KEY = "pudans-pending-oidc-bind";
const IDENTITY_PATH = "/identity-providers";
const PASSKEY_PROVIDER = "passkey";

const ERROR_MESSAGES = {
  notFound: "身份绑定不存在。",
  conflict: "操作失败，请稍后重试。",
};

type PendingOidcBind = {
  registrationId: string;
  redirectUri: string;
};

export function AccountIdentitiesView() {
  const queryClient = useQueryClient();
  const [pendingUnbind, setPendingUnbind] = useState<UserConnection | null>(null);
  const handledCallback = useRef(false);

  const connectionsQuery = useQuery({
    queryKey: CONNECTIONS_QUERY_KEY,
    queryFn: async () => (await apiClient.userConnection.listMyConnections({})).data,
  });
  const oidcProvidersQuery = useQuery({
    queryKey: ["account-oidc-providers"],
    queryFn: async () => (await apiClient.userConnection.listOidcProviders()).data,
  });
  const loginProvidersQuery = useQuery({
    queryKey: ["login-providers"],
    queryFn: async () => (await apiClient.login.listLoginProviders()).data,
  });

  const connections = (connectionsQuery.data ?? []).filter(
    (connection) => connection.spec.registrationId !== PASSKEY_PROVIDER,
  );
  const oidcProviders = oidcProvidersQuery.data ?? [];
  const feishuProvider = (loginProvidersQuery.data ?? []).find(
    (provider) => provider.name?.toLowerCase() === "feishu" && provider.authenticationUrl,
  );

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: CONNECTIONS_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: ["account-oidc-providers"] });
  };

  useEffect(() => {
    if (handledCallback.current) {
      return;
    }
    handledCallback.current = true;
    const params = new URLSearchParams(window.location.search);
    const cleanUrl = () => window.history.replaceState({}, "", window.location.pathname);
    const providerError = params.get("error");
    const bound = params.get("bound");
    const code = params.get("code");
    const state = params.get("state");
    const detail = params.get("detail");
    const pending = sessionStorage.getItem(OIDC_PENDING_KEY);

    if (providerError) {
      toast.error(providerError === "access_denied" ? "已取消身份绑定。" : "身份绑定失败。");
      cleanUrl();
      return;
    }
    if (bound === "feishu") {
      toast.success("飞书身份已绑定。");
      cleanUrl();
      void queryClient.invalidateQueries({ queryKey: CONNECTIONS_QUERY_KEY });
      return;
    }
    if (code && state && pending) {
      sessionStorage.removeItem(OIDC_PENDING_KEY);
      const request = JSON.parse(pending) as PendingOidcBind;
      void apiClient.userConnection
        .bindOidcIdentity({
          oidcBindRequest: {
            registrationId: request.registrationId,
            code,
            state,
            redirectUri: request.redirectUri,
          },
        })
        .then(() => {
          toast.success("身份已绑定。");
          cleanUrl();
          void queryClient.invalidateQueries({ queryKey: CONNECTIONS_QUERY_KEY });
        })
        .catch((error: unknown) => {
          toast.error(resolveApiError(error, ERROR_MESSAGES));
          cleanUrl();
        });
      return;
    }
    if (code && detail) {
      toast.error(detail);
      cleanUrl();
    }
  }, [queryClient]);

  const unbindMutation = useMutation({
    mutationFn: async (name: string) => {
      await apiClient.userConnection.unbindConnection({ name });
    },
    onSuccess: () => {
      toast.success("身份已解绑。");
      setPendingUnbind(null);
      refresh();
    },
    onError: (error) => {
      setPendingUnbind(null);
      toast.error(resolveApiError(error, ERROR_MESSAGES));
    },
  });

  const bindFeishu = () => {
    if (!feishuProvider?.authenticationUrl) {
      toast.error("飞书登录未配置。");
      return;
    }
    const target = new URL(feishuProvider.authenticationUrl, window.location.origin);
    target.searchParams.set("bind", "true");
    window.location.assign(target.toString());
  };

  const bindOidc = async (registrationId: string) => {
    const redirectUri = `${window.location.origin}${IDENTITY_PATH}`;
    try {
      const response = await apiClient.userConnection.getOidcAuthorizeUrl({
        registrationId,
        redirectUri,
      });
      if (!response.data.authorizationUrl) {
        throw new Error("缺少 OIDC 授权地址。");
      }
      const pending: PendingOidcBind = { registrationId, redirectUri };
      sessionStorage.setItem(OIDC_PENDING_KEY, JSON.stringify(pending));
      window.location.assign(response.data.authorizationUrl);
    } catch (error) {
      toast.error(isAxiosError(error) ? resolveApiError(error, ERROR_MESSAGES) : "无法发起绑定。");
    }
  };

  const boundProviderIds = new Set(connections.map((connection) => connection.spec.registrationId));
  const availableOidcProviders = oidcProviders.filter(
    (provider) => provider.registrationId && !boundProviderIds.has(provider.registrationId),
  );
  const feishuBound = boundProviderIds.has("feishu");

  return (
    <ConsolePage title="身份提供商">
      <ConsoleSection>
        <ConsoleSectionHeader
          title="已绑定身份"
          description="已关联的飞书或其他身份提供商，可用于免密码登录。"
        />
        {connectionsQuery.isPending ? (
          <div className="flex min-h-24 items-center justify-center">
            <Loader2 className="size-4 animate-spin text-muted-foreground" />
          </div>
        ) : connectionsQuery.isError ? (
          <ConsoleEmpty>无法读取身份绑定。</ConsoleEmpty>
        ) : connections.length === 0 ? (
          <ConsoleEmpty>尚未绑定任何身份提供商。</ConsoleEmpty>
        ) : (
          <div className="divide-y">
            {connections.map((connection) => (
              <div key={connection.metadata.name} className="flex items-center gap-3 px-5 py-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                  <Link2 className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">
                    {providerLabel(connection.spec.registrationId, oidcProviders)}
                  </p>
                  <p className="truncate font-mono text-xs text-muted-foreground">
                    {connection.spec.providerUserId}
                  </p>
                </div>
                <Badge variant="secondary">
                  绑定于 {formatUserDateTime(connection.spec.updatedAt)}
                </Badge>
                <Button variant="ghost" size="sm" onClick={() => setPendingUnbind(connection)}>
                  <Unlink />
                  解绑
                </Button>
              </div>
            ))}
          </div>
        )}
      </ConsoleSection>

      <ConsoleSection>
        <ConsoleSectionHeader
          title="可绑定身份"
          description="绑定后可以使用对应的第三方账号登录 Pudans。"
        />
        <div className="divide-y">
          {!feishuBound && feishuProvider ? (
            <ProviderRow
              name={feishuProvider.displayName ?? "飞书"}
              description="使用飞书账号扫码登录"
              onBind={bindFeishu}
            />
          ) : null}
          {availableOidcProviders.map((provider) => (
            <ProviderRow
              key={provider.registrationId}
              name={provider.displayName ?? provider.registrationId ?? "OIDC"}
              description={provider.issuerUri ?? "OIDC 身份提供商"}
              onBind={() => {
                if (provider.registrationId) {
                  void bindOidc(provider.registrationId);
                }
              }}
            />
          ))}
        </div>
        {(feishuBound || !feishuProvider) && availableOidcProviders.length === 0 ? (
          <ConsoleEmpty>没有其他可绑定的身份提供商。</ConsoleEmpty>
        ) : null}
      </ConsoleSection>

      <ConfirmDialog
        open={pendingUnbind !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingUnbind(null);
          }
        }}
        title="解绑身份"
        desc={
          pendingUnbind
            ? `确定解绑「${providerLabel(pendingUnbind.spec.registrationId, oidcProviders)}」？解绑后将无法使用该身份登录。`
            : ""
        }
        destructive
        confirmText="解绑"
        isLoading={unbindMutation.isPending}
        handleConfirm={() => {
          if (pendingUnbind) {
            unbindMutation.mutate(pendingUnbind.metadata.name);
          }
        }}
      />
    </ConsolePage>
  );
}

function providerLabel(
  registrationId: string,
  oidcProviders: Array<{ registrationId?: string; displayName?: string }>,
): string {
  if (registrationId === "feishu") {
    return "飞书";
  }
  return (
    oidcProviders.find((provider) => provider.registrationId === registrationId)?.displayName ??
    registrationId
  );
}

function ProviderRow({
  name,
  description,
  onBind,
}: {
  name: string;
  description: string;
  onBind: () => void;
}) {
  return (
    <div className="flex items-center gap-3 px-5 py-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
        <Link2 className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">{name}</p>
        <p className="truncate text-xs text-muted-foreground">{description}</p>
      </div>
      <Button variant="outline" size="sm" onClick={onBind}>
        <Plus />
        绑定
      </Button>
    </div>
  );
}
