import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { ParameterGroupPanel } from "@/modules/workspace/console/settings/components/parameter-group-panel";
import {
  resolveParameterSettingError,
  updateParameterGroup,
} from "@/modules/workspace/console/settings/lib/parameter-api";

import { AddIdentityCard, IdentityCard } from "./components/identity-card";
import type { IdentityKind } from "./components/identity-icon";
import { PolicyDialog } from "./components/policy-dialog";
import { ProviderDialog } from "./components/provider-dialog";
import {
  AUTHENTICATION_GROUP,
  FEISHU_FIELDS,
  PAT_FIELDS,
  type ProviderKind,
  type ProviderValues,
  TOTP_FIELDS,
  readBoolean,
  readNumber,
  readObject,
  readProviders,
  readString,
} from "./lib/authentication-fields";

const AUTHENTICATION_PANEL = { key: AUTHENTICATION_GROUP, fields: [] };

type EditTarget =
  { type: "totp" | "feishu" | "pat" } | { type: ProviderKind; index: number | null };

type DeleteTarget = {
  kind: ProviderKind;
  index: number;
};

type IdentityItem =
  | {
      type: "card";
      key: string;
      kind: IdentityKind;
      title: string;
      tags: string[];
      description: string;
      onEdit: () => void;
      onDelete?: () => void;
    }
  | {
      type: "add";
      key: string;
      label: string;
      onClick: () => void;
    };

export function IdentityProviderView() {
  return (
    <ConsolePage title="身份提供商">
      <ParameterGroupPanel
        group={AUTHENTICATION_PANEL}
        render={(data) => <IdentityProviderContent data={data} />}
      />
    </ConsolePage>
  );
}

function IdentityProviderContent({ data }: { data: Record<string, unknown> }) {
  const queryClient = useQueryClient();
  const [target, setTarget] = useState<EditTarget | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  const totp = readObject(data, "totp");
  const feishu = readObject(data, "feishu");
  const pat = readObject(data, "pat");
  const oauth2Providers = readProviders(data, "oauth2");
  const oidcProviders = readProviders(data, "oidc");

  const deleteMutation = useMutation({
    mutationFn: async ({ kind, index }: DeleteTarget) => {
      const providers = readProviders(data, kind);
      const nextProviders = providers.filter((_, providerIndex) => providerIndex !== index);
      await updateParameterGroup(AUTHENTICATION_GROUP, { [kind]: { providers: nextProviders } });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["parameter-setting", AUTHENTICATION_GROUP],
      });
      setDeleteTarget(null);
      toast.success("提供方已删除。");
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const items = buildItems({
    totp,
    feishu,
    pat,
    oauth2Providers,
    oidcProviders,
    onEdit: setTarget,
    onDelete: setDeleteTarget,
  });

  const deletingProvider = deleteTarget
    ? readProviders(data, deleteTarget.kind)[deleteTarget.index]
    : undefined;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-2">
        {items.map((item) =>
          item.type === "card" ? (
            <IdentityCard
              key={item.key}
              kind={item.kind}
              title={item.title}
              tags={item.tags}
              description={item.description}
              onEdit={item.onEdit}
              onDelete={item.onDelete}
            />
          ) : (
            <AddIdentityCard key={item.key} label={item.label} onClick={item.onClick} />
          ),
        )}
      </div>

      {target?.type === "totp" ? (
        <PolicyDialog
          kind="totp"
          title="TOTP 两步验证"
          description="控制全局 TOTP 开关与认证器签发方。"
          fields={TOTP_FIELDS}
          data={data}
          onClose={() => setTarget(null)}
        />
      ) : null}
      {target?.type === "feishu" ? (
        <PolicyDialog
          kind="feishu"
          title="飞书"
          description="配置飞书开放平台应用与用户资料同步。"
          fields={FEISHU_FIELDS}
          data={data}
          onClose={() => setTarget(null)}
        />
      ) : null}
      {target?.type === "pat" ? (
        <PolicyDialog
          kind="pat"
          title="个人访问令牌"
          description="配置 PAT 的创建策略与有效期上限。"
          fields={PAT_FIELDS}
          data={data}
          onClose={() => setTarget(null)}
        />
      ) : null}
      {target && (target.type === "oauth2" || target.type === "oidc") ? (
        <ProviderDialog
          kind={target.type}
          index={target.index}
          providers={readProviders(data, target.type)}
          onClose={() => setTarget(null)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除提供方"
        desc={
          deletingProvider
            ? `确定删除「${deletingProvider.displayName || deletingProvider.registrationId}」？删除后该提供方将不再出现在登录页。`
            : "确定删除该提供方？"
        }
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </div>
  );
}

type BuildItemsInput = {
  totp: Record<string, unknown>;
  feishu: Record<string, unknown>;
  pat: Record<string, unknown>;
  oauth2Providers: ProviderValues[];
  oidcProviders: ProviderValues[];
  onEdit: (target: EditTarget) => void;
  onDelete: (target: DeleteTarget) => void;
};

function buildItems({
  totp,
  feishu,
  pat,
  oauth2Providers,
  oidcProviders,
  onEdit,
  onDelete,
}: BuildItemsInput): IdentityItem[] {
  const items: IdentityItem[] = [
    {
      type: "card",
      key: "totp",
      kind: "totp",
      title: "TOTP 两步验证",
      tags: ["TOTP", readBoolean(totp.enabled, true) ? "已启用" : "已停用"],
      description: `签发方：${readString(totp.issuer) || "pudans"}`,
      onEdit: () => onEdit({ type: "totp" }),
    },
  ];

  const feishuEnabled = typeof feishu.enabled === "boolean" ? feishu.enabled : null;
  const feishuAppId = readString(feishu.appId);
  const feishuInherited = feishuEnabled === null;
  items.push({
    type: "card",
    key: "feishu",
    kind: "feishu",
    title: "飞书",
    tags: [
      "飞书",
      feishuInherited
        ? "沿用环境配置"
        : feishuEnabled
          ? feishuAppId
            ? "已启用"
            : "待完善"
          : "已停用",
    ],
    description: `App ID：${feishuAppId || (feishuInherited ? "沿用环境配置" : "未配置")}`,
    onEdit: () => onEdit({ type: "feishu" }),
  });

  const patEnabled = readBoolean(pat.enabled, true);
  const maxLifetimeDays = readNumber(pat.maxLifetimeDays, 0);
  items.push({
    type: "card",
    key: "pat",
    kind: "pat",
    title: "个人访问令牌",
    tags: ["PAT", patEnabled ? "已启用" : "已停用"],
    description: `最长有效期：${maxLifetimeDays > 0 ? `${maxLifetimeDays} 天` : "不限"}`,
    onEdit: () => onEdit({ type: "pat" }),
  });

  oauth2Providers.forEach((provider, index) => {
    items.push(providerCard("oauth2", provider, index, onEdit, onDelete));
  });
  items.push({
    type: "add",
    key: "add-oauth2",
    label: "添加 OAuth2 提供方",
    onClick: () => onEdit({ type: "oauth2", index: null }),
  });

  oidcProviders.forEach((provider, index) => {
    items.push(providerCard("oidc", provider, index, onEdit, onDelete));
  });
  items.push({
    type: "add",
    key: "add-oidc",
    label: "添加 OIDC 提供方",
    onClick: () => onEdit({ type: "oidc", index: null }),
  });

  return items;
}

function providerCard(
  kind: ProviderKind,
  provider: ProviderValues,
  index: number,
  onEdit: (target: EditTarget) => void,
  onDelete: (target: DeleteTarget) => void,
): IdentityItem {
  return {
    type: "card",
    key: `${kind}-${provider.registrationId || index}`,
    kind,
    title: provider.displayName || provider.registrationId || "未命名提供方",
    tags: [kind === "oauth2" ? "OAuth2" : "OIDC", provider.enabled ? "已启用" : "已停用"],
    description:
      kind === "oauth2"
        ? `Client ID：${provider.clientId || "未配置"}`
        : `Issuer：${provider.issuerUri || "未配置"}`,
    onEdit: () => onEdit({ type: kind, index }),
    onDelete: () => onDelete({ kind, index }),
  };
}
