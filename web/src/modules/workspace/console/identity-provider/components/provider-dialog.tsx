import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { ParameterFieldControl } from "@/modules/workspace/console/settings/components/parameter-field";
import {
  resolveParameterSettingError,
  updateParameterGroup,
} from "@/modules/workspace/console/settings/lib/parameter-api";
import {
  type ParameterField,
  type ParameterFieldSet,
  type ParameterFormValues,
  createParameterFormValues,
  createParameterSchema,
  groupParameterFieldsBySection,
} from "@/modules/workspace/console/settings/lib/parameter-fields";

import {
  AUTHENTICATION_GROUP,
  type ProviderKind,
  type ProviderValues,
  emptyProvider,
} from "../lib/authentication-fields";
import { IdentityIcon } from "./identity-icon";
import { TestConnectionButton } from "./test-connection-button";

type ProviderDialogProps = {
  kind: ProviderKind;
  index: number | null;
  providers: readonly ProviderValues[];
  onClose: () => void;
};

export function ProviderDialog({ kind, index, providers, onClose }: ProviderDialogProps) {
  const queryClient = useQueryClient();
  const fields = providerFields(kind);
  const initialProvider =
    index == null ? emptyProvider(kind) : (providers[index] ?? emptyProvider(kind));
  const form = useForm<ParameterFormValues>({
    resolver: zodResolver(createParameterSchema(fields)) as Resolver<ParameterFormValues>,
    defaultValues: createParameterFormValues(
      fields,
      initialProvider as unknown as Record<string, unknown>,
    ),
  });

  const mutation = useMutation({
    mutationFn: (nextProviders: ProviderValues[]) =>
      updateParameterGroup(AUTHENTICATION_GROUP, { [kind]: { providers: nextProviders } }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["parameter-setting", AUTHENTICATION_GROUP],
      });
      toast.success("身份认证提供方已保存。");
      onClose();
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      const provider = toProvider(kind, values);
      const duplicated = providers.some(
        (candidate, candidateIndex) =>
          candidateIndex !== index && candidate.registrationId === provider.registrationId,
      );
      if (duplicated) {
        form.setError("registrationId", { message: "该注册 ID 已被使用。" });
        return;
      }

      const nextProviders = [...providers];
      if (index == null) {
        nextProviders.push(provider);
      } else {
        nextProviders[index] = provider;
      }
      mutation.mutate(nextProviders);
    },
    () => toast.error("请检查提供方配置。"),
  );

  const title =
    index == null ? `添加 ${kind === "oauth2" ? "OAuth2" : "OIDC"} 提供方` : "编辑提供方";

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<IdentityIcon kind={kind} size={24} />}
      title={title}
      description="登录页会展示已启用的提供方；修改注册 ID 后需要重新填写 Client Secret。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      maxHeightRatio={0.5}
      contentClassName="sm:max-w-2xl"
      formClassName="space-y-6"
      headerActions={
        <TestConnectionButton
          buildRequest={() => {
            const values = form.getValues();
            return {
              type: kind,
              registrationId: stringValue(values.registrationId),
              issuerUri: kind === "oidc" ? stringValue(values.issuerUri) : undefined,
              authorizationUri: stringValue(values.authorizationUri),
              tokenUri: stringValue(values.tokenUri),
              userInfoUri: stringValue(values.userInfoUri),
              clientId: stringValue(values.clientId),
              clientSecret: stringValue(values.clientSecret),
              scopes: stringValue(values.scopes),
            };
          }}
        />
      }
    >
      {groupParameterFieldsBySection(fields.fields).map((section, sectionIndex) => (
        <FormDialogSection
          key={`${section.title ?? "default"}-${sectionIndex}`}
          title={section.title}
        >
          <div className="grid gap-4 md:grid-cols-2">
            {section.fields.map((field) => (
              <ParameterFieldControl
                key={field.name}
                form={form}
                groupKey="identity-provider"
                field={field}
                userGroupOptions={[]}
                userGroupsPending={false}
              />
            ))}
          </div>
        </FormDialogSection>
      ))}
    </FormDialog>
  );
}

function providerFields(kind: ProviderKind): ParameterFieldSet {
  const fields: ParameterField[] = [
    {
      name: "registrationId",
      label: "注册 ID",
      kind: "text",
      section: "应用信息",
      required: true,
      maxLength: 63,
      hint: "小写字母、数字、- 和 .，用于 OAuth2/OIDC 回调路径。",
    },
    {
      name: "displayName",
      label: "显示名称",
      kind: "text",
      section: "应用信息",
      maxLength: 128,
    },
    {
      name: "clientId",
      label: "Client ID",
      kind: "text",
      section: "应用信息",
      required: true,
      maxLength: 512,
    },
    {
      name: "clientSecret",
      label: "Client Secret",
      kind: "password",
      section: "应用信息",
      maxLength: 1024,
      hint: "保持 ******** 表示沿用已保存的密钥。",
    },
    {
      name: "logo",
      label: "Logo 地址",
      kind: "url",
      section: "应用信息",
      maxLength: 2048,
    },
  ];

  if (kind === "oidc") {
    fields.push({
      name: "issuerUri",
      label: "Issuer URI",
      kind: "url",
      section: "端点",
      maxLength: 2048,
    });
  }

  fields.push(
    {
      name: "authorizationUri",
      label: "授权地址",
      kind: "url",
      section: "端点",
      required: true,
      maxLength: 2048,
    },
    {
      name: "tokenUri",
      label: "令牌地址",
      kind: "url",
      section: "端点",
      required: true,
      maxLength: 2048,
    },
    {
      name: "userInfoUri",
      label: "用户信息地址",
      kind: "url",
      section: "端点",
      required: true,
      maxLength: 2048,
    },
    {
      name: "userNameAttribute",
      label: "用户标识字段",
      kind: "text",
      section: "端点",
      maxLength: 128,
      hint: kind === "oidc" ? "默认使用 sub。" : "默认使用 id。",
    },
    {
      name: "scopes",
      label: "权限范围",
      kind: "text",
      section: "端点",
      maxLength: 1024,
      hint: "以空格分隔。",
    },
    {
      name: "enabled",
      label: "启用提供方",
      kind: "boolean",
      section: "状态",
      hint: "关闭后不会出现在登录页，也不会参与 OAuth2 客户端注册。",
    },
  );

  return { fields };
}

function toProvider(kind: ProviderKind, values: ParameterFormValues): ProviderValues {
  return {
    registrationId: stringValue(values.registrationId).trim(),
    displayName: stringValue(values.displayName).trim(),
    logo: stringValue(values.logo).trim(),
    clientId: stringValue(values.clientId).trim(),
    clientSecret: stringValue(values.clientSecret),
    issuerUri: kind === "oidc" ? stringValue(values.issuerUri).trim() : "",
    authorizationUri: stringValue(values.authorizationUri).trim(),
    tokenUri: stringValue(values.tokenUri).trim(),
    userInfoUri: stringValue(values.userInfoUri).trim(),
    userNameAttribute: stringValue(values.userNameAttribute).trim(),
    scopes: stringValue(values.scopes).trim(),
    enabled: Boolean(values.enabled),
  };
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}
