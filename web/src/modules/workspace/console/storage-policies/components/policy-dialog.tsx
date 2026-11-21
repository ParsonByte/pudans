import { useState } from "react";
import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ExternalLink, Loader2, PlugZap, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

import {
  type StoragePolicyCredentials,
  type StoragePolicyItem,
  type StoragePolicySettings,
  type StoragePolicySpec,
  type StoragePolicyTypeDescriptor,
  createStoragePolicy,
  fetchOneDriveAuthorizeUrl,
  fetchOneDriveStatus,
  resolveOneDriveRoot,
  resolveStorageApiError,
  testStoragePolicy,
  updateStoragePolicy,
  validateStoragePolicy,
} from "../lib/api";
import {
  bytesToMegabytes,
  formatFileTypes,
  isLocal,
  isOneDrive,
  megabytesToBytes,
  minimumChunkMegabytes,
  parseFileTypes,
  requiresEndpoint,
  requiresStoredCredentials,
  supportsCors,
  supportsRegion,
  supportsS3PathStyle,
} from "../lib/model";
import { CorsDialog } from "./cors-dialog";
import { StorageTypeIcon } from "./type-icon";

type PolicyDialogProps = {
  mode: "create" | "edit";
  policy: StoragePolicyItem;
  descriptor?: StoragePolicyTypeDescriptor;
  canTest: boolean;
  canOAuth: boolean;
  onClose: () => void;
};

const storageTypeSchema = z.enum([
  "local",
  "s3",
  "ks3",
  "oss",
  "cos",
  "obs",
  "qiniu",
  "upyun",
  "onedrive",
]);

const policySchema = z.object({
  type: storageTypeSchema,
  displayName: z.string().trim().min(1, "请输入策略名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
  bucketName: z.string().trim().max(255, "Bucket 名称过长。"),
  privateBucket: z.boolean(),
  server: z.string().trim().max(500, "EndPoint 过长。"),
  region: z.string().trim().max(100, "区域代码过长。"),
  tenantId: z.string().trim().max(100, "租户 ID 过长。"),
  driveId: z.string().trim().max(500, "驱动器 ID 过长。"),
  accessKey: z.string().trim().max(500, "Access Key 过长。"),
  secretKey: z.string().trim().max(1000, "Secret Key 过长。"),
  token: z.string().trim().max(2000, "令牌过长。"),
  clientSecret: z.string().trim().max(1000, "客户端密钥过长。"),
  maxSizeMb: z.coerce.number().min(0, "大小不能为负数。").max(1_048_576, "大小上限过大。"),
  dirNameRule: z.string().trim().min(1, "请输入目录规则。").max(512, "目录规则过长。"),
  fileNameRule: z.string().trim().min(1, "请输入文件规则。").max(512, "文件规则过长。"),
  chunkSizeMb: z.coerce.number().min(0, "分片大小不能为负数。").max(5120, "分片不能超过 5 GB。"),
  fileTypes: z.string().trim().max(8192, "文件类型列表过长。"),
  fileTypeDenyList: z.boolean(),
  nameRegexp: z.string().trim().max(512, "文件名正则过长。"),
  nameRegexpDenyList: z.boolean(),
  proxyDownload: z.boolean(),
  defaultPolicy: z.boolean(),
  deleteBatchSize: z.coerce
    .number()
    .int()
    .min(1, "批量删除数量至少为 1。")
    .max(1000, "批量删除数量不能超过 1000。"),
  s3PathStyle: z.boolean(),
});

type PolicyFormValues = z.infer<typeof policySchema>;

function buildPolicySchema(mode: "create" | "edit") {
  return policySchema.superRefine((values, context) => {
    if (!isLocal(values.type) && !values.bucketName) {
      context.addIssue({
        code: "custom",
        path: ["bucketName"],
        message: isOneDrive(values.type) ? "请输入客户端 ID。" : "请输入 Bucket 名称。",
      });
    }
    if (requiresEndpoint(values.type) && !values.server) {
      context.addIssue({ code: "custom", path: ["server"], message: "请输入 EndPoint。" });
    }
    if (values.dirNameRule.includes("..") || values.fileNameRule.includes("..")) {
      context.addIssue({
        code: "custom",
        path: ["dirNameRule"],
        message: "目录和文件规则不能包含上级目录。",
      });
    }
    if (values.chunkSizeMb > 0 && values.chunkSizeMb < minimumChunkMegabytes(values.type)) {
      context.addIssue({
        code: "custom",
        path: ["chunkSizeMb"],
        message: `分片大小不能小于 ${minimumChunkMegabytes(values.type)} MB。`,
      });
    }
    if (values.nameRegexp) {
      try {
        new RegExp(values.nameRegexp);
      } catch {
        context.addIssue({
          code: "custom",
          path: ["nameRegexp"],
          message: "文件名正则无效。",
        });
      }
    }
    if (mode !== "create") {
      return;
    }
    if (isOneDrive(values.type)) {
      if (!values.clientSecret) {
        context.addIssue({
          code: "custom",
          path: ["clientSecret"],
          message: "请输入 OneDrive 客户端密钥。",
        });
      }
      if (!values.token) {
        context.addIssue({
          code: "custom",
          path: ["token"],
          message: "请输入 OneDrive 刷新令牌。",
        });
      }
      return;
    }
    if (isLocal(values.type)) {
      return;
    }
    if (!values.accessKey) {
      context.addIssue({
        code: "custom",
        path: ["accessKey"],
        message: values.type === "upyun" ? "请输入操作员。" : "请输入 Access Key。",
      });
    }
    if (!values.secretKey) {
      context.addIssue({
        code: "custom",
        path: ["secretKey"],
        message: values.type === "upyun" ? "请输入操作员密码。" : "请输入 Secret Key。",
      });
    }
  });
}

function toFormValues(policy: StoragePolicyItem): PolicyFormValues {
  const spec = policy.spec;
  return {
    type: spec.type,
    displayName: spec.displayName ?? "",
    description: spec.description ?? "",
    bucketName: spec.bucketName ?? "",
    privateBucket: spec.privateBucket ?? false,
    server: spec.server ?? "",
    region: spec.settings?.region ?? "",
    tenantId: spec.settings?.tenantId ?? (isOneDrive(spec.type) ? "common" : ""),
    driveId: spec.settings?.driveId ?? "",
    accessKey: "",
    secretKey: "",
    token: "",
    clientSecret: "",
    maxSizeMb: bytesToMegabytes(spec.maxSize),
    dirNameRule: spec.dirNameRule ?? "uploads/{path}",
    fileNameRule: spec.fileNameRule ?? "{randomkey8}_{originname}",
    chunkSizeMb: bytesToMegabytes(spec.chunkSize),
    fileTypes: formatFileTypes(spec.fileTypes),
    fileTypeDenyList: spec.fileTypeDenyList ?? false,
    nameRegexp: spec.nameRegexp ?? "",
    nameRegexpDenyList: spec.nameRegexpDenyList ?? false,
    proxyDownload: spec.proxyDownload ?? true,
    defaultPolicy: spec.defaultPolicy ?? false,
    deleteBatchSize: spec.settings?.deleteBatchSize ?? 1000,
    s3PathStyle: spec.settings?.s3PathStyle ?? false,
  };
}

function toPolicyItem(policy: StoragePolicyItem, values: PolicyFormValues): StoragePolicyItem {
  const type = values.type;
  const credentials: StoragePolicyCredentials = {};
  if (values.accessKey) {
    credentials.accessKey = values.accessKey;
  }
  if (values.secretKey) {
    credentials.secretKey = values.secretKey;
  }
  if (values.token) {
    credentials.token = values.token;
  }
  if (values.clientSecret) {
    credentials.clientSecret = values.clientSecret;
  }
  const settings: StoragePolicySettings = {
    deleteBatchSize: values.deleteBatchSize,
  };
  if (supportsRegion(type) && values.region) {
    settings.region = values.region;
  }
  if (supportsS3PathStyle(type)) {
    settings.s3PathStyle = values.s3PathStyle;
  }
  if (isOneDrive(type)) {
    settings.tenantId = values.tenantId || "common";
    if (values.driveId) {
      settings.driveId = values.driveId;
    }
  }
  const spec: StoragePolicySpec = {
    ...policy.spec,
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    type,
    server: requiresEndpoint(type) || values.server ? values.server.trim() || undefined : undefined,
    bucketName: isLocal(type) ? undefined : values.bucketName.trim() || undefined,
    privateBucket: isLocal(type) ? false : values.privateBucket,
    maxSize: megabytesToBytes(values.maxSizeMb),
    dirNameRule: values.dirNameRule.trim(),
    fileNameRule: values.fileNameRule.trim(),
    chunkSize: megabytesToBytes(values.chunkSizeMb),
    fileTypes: parseFileTypes(values.fileTypes),
    fileTypeDenyList: values.fileTypeDenyList,
    nameRegexp: values.nameRegexp.trim() || undefined,
    nameRegexpDenyList: values.nameRegexpDenyList,
    proxyDownload: values.proxyDownload,
    defaultPolicy: values.defaultPolicy,
    credentials,
    settings,
  };
  return { ...policy, spec };
}

class ValidationError extends Error {}

export function PolicyDialog({
  mode,
  policy,
  descriptor,
  canTest,
  canOAuth,
  onClose,
}: PolicyDialogProps) {
  const queryClient = useQueryClient();
  const [corsOpen, setCorsOpen] = useState(false);
  const [siteUrl, setSiteUrl] = useState("");
  const form = useForm<PolicyFormValues>({
    resolver: zodResolver(buildPolicySchema(mode)) as Resolver<PolicyFormValues>,
    defaultValues: toFormValues(policy),
  });
  const type = useWatch({ control: form.control, name: "type" });
  const oneDrive = isOneDrive(type);
  const oauthEnabled = mode === "edit" && oneDrive && canOAuth;

  const oauthStatusQuery = useQuery({
    queryKey: ["storage-policy-oauth", policy.metadata.name],
    queryFn: () => fetchOneDriveStatus(policy.metadata.name),
    enabled: oauthEnabled,
    retry: false,
  });

  const saveMutation = useMutation({
    mutationFn: async (values: PolicyFormValues) => {
      const next = toPolicyItem(policy, values);
      const credentialsChanged = Boolean(
        values.accessKey || values.secretKey || values.token || values.clientSecret,
      );
      // 编辑时留空凭据表示保留服务端已有值，先跳过对空凭据的校验。
      if (mode === "create" || credentialsChanged) {
        const validation = await validateStoragePolicy(next);
        if (!validation.valid) {
          throw new ValidationError(validation.errors.join(" "));
        }
      }
      return mode === "create" ? createStoragePolicy(next) : updateStoragePolicy(next);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["storage-policies"] });
      toast.success(mode === "create" ? "存储策略已创建。" : "存储策略已保存。");
      onClose();
    },
    onError: (error) => {
      if (error instanceof ValidationError) {
        toast.error(error.message);
        return;
      }
      toast.error(resolveStorageApiError(error));
    },
  });

  const testMutation = useMutation({
    mutationFn: () => testStoragePolicy(policy.metadata.name),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["storage-policies"] });
      if (result.success) {
        toast.success(result.message || "存储连接正常。");
      } else {
        toast.error(result.message || "存储连接失败。");
      }
    },
    onError: (error) => toast.error(resolveStorageApiError(error)),
  });

  const authorizeMutation = useMutation({
    mutationFn: () => fetchOneDriveAuthorizeUrl(policy.metadata.name),
    onSuccess: (result) => {
      window.open(result.url, "_blank", "noopener,noreferrer");
    },
    onError: (error) => toast.error(resolveStorageApiError(error)),
  });

  const resolveRootMutation = useMutation({
    mutationFn: () => resolveOneDriveRoot(policy.metadata.name, siteUrl.trim()),
    onSuccess: (root) => {
      form.setValue("driveId", root.driveId, { shouldDirty: true, shouldValidate: true });
      toast.success("已解析 SharePoint 驱动器 ID。");
    },
    onError: (error) => toast.error(resolveStorageApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const headerActions =
    mode === "edit" ? (
      <div className="flex flex-wrap items-center gap-2">
        {canTest ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={testMutation.isPending}
            onClick={() => testMutation.mutate()}
          >
            {testMutation.isPending ? <Loader2 className="animate-spin" /> : <PlugZap />}
            测试连接
          </Button>
        ) : null}
        {canTest && supportsCors(descriptor) ? (
          <Button type="button" variant="outline" size="sm" onClick={() => setCorsOpen(true)}>
            跨域配置
          </Button>
        ) : null}
      </div>
    ) : null;

  return (
    <>
      <FormDialog
        open
        onClose={onClose}
        icon={<StorageTypeIcon type={type} className="size-5" />}
        title={mode === "create" ? "添加存储策略" : `编辑 ${policy.spec.displayName}`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{descriptor?.displayName ?? type}</Badge>
            {mode === "edit" && policy.status?.ready === false ? (
              <span className="text-xs text-muted-foreground">
                {policy.status.message || "策略尚未就绪。"}
              </span>
            ) : null}
          </span>
        }
        form={form}
        onSubmit={onSubmit}
        isPending={saveMutation.isPending}
        contentClassName="h-[85vh] max-h-[85vh] sm:max-w-3xl"
        fill
        submitLabel={mode === "create" ? "创建存储策略" : "保存更改"}
        headerActions={headerActions}
      >
        <FormDialogSection title="基本信息">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label="名称"
              htmlFor="storage-display-name"
              error={form.formState.errors.displayName?.message}
            >
              <Input
                id="storage-display-name"
                placeholder="例如：主对象存储"
                {...form.register("displayName")}
              />
            </FormField>
            <FormField
              label={isOneDrive(type) ? "客户端 ID" : "Bucket 名称"}
              htmlFor="storage-bucket"
              error={form.formState.errors.bucketName?.message}
              hint={isOneDrive(type) ? "Microsoft Entra 应用的客户端 ID。" : undefined}
            >
              <Input
                id="storage-bucket"
                placeholder={isOneDrive(type) ? "client-id" : "bucket-name"}
                disabled={isLocal(type)}
                {...form.register("bucketName")}
              />
            </FormField>
          </div>
          <FormField
            label="描述"
            htmlFor="storage-description"
            error={form.formState.errors.description?.message}
          >
            <Textarea
              id="storage-description"
              rows={2}
              placeholder="说明该存储的用途。"
              {...form.register("description")}
            />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            {!isLocal(type) && !oneDrive ? (
              <div className="flex items-center justify-between gap-4 border p-4">
                <div className="space-y-1">
                  <p className="text-sm font-medium">私有 Bucket</p>
                  <p className="text-xs text-muted-foreground">私有桶通过签名 URL 访问文件。</p>
                </div>
                <Controller
                  control={form.control}
                  name="privateBucket"
                  render={({ field }) => (
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-label="私有 Bucket"
                    />
                  )}
                />
              </div>
            ) : null}
            <div className="flex items-center justify-between gap-4 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">默认策略</p>
                <p className="text-xs text-muted-foreground">未指定策略时使用该存储。</p>
              </div>
              <Controller
                control={form.control}
                name="defaultPolicy"
                render={({ field }) => (
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    aria-label="默认策略"
                  />
                )}
              />
            </div>
            <div className="flex items-center justify-between gap-4 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">下载中转</p>
                <p className="text-xs text-muted-foreground">下载请求经后端代理转发。</p>
              </div>
              <Controller
                control={form.control}
                name="proxyDownload"
                render={({ field }) => (
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    aria-label="下载中转"
                  />
                )}
              />
            </div>
          </div>
        </FormDialogSection>

        {requiresEndpoint(type) || supportsRegion(type) ? (
          <FormDialogSection title="连接信息">
            <div className="grid gap-4 sm:grid-cols-2">
              {requiresEndpoint(type) ? (
                <FormField
                  label="EndPoint"
                  htmlFor="storage-server"
                  error={form.formState.errors.server?.message}
                  hint={isLocal(type) ? undefined : "对象存储访问域名。"}
                >
                  <Input
                    id="storage-server"
                    placeholder="oss-cn-hangzhou.aliyuncs.com"
                    {...form.register("server")}
                  />
                </FormField>
              ) : null}
              {supportsRegion(type) ? (
                <FormField
                  label="区域代码"
                  htmlFor="storage-region"
                  error={form.formState.errors.region?.message}
                  hint="例如 cn-hangzhou、ap-guangzhou。"
                >
                  <Input
                    id="storage-region"
                    placeholder="cn-hangzhou"
                    {...form.register("region")}
                  />
                </FormField>
              ) : null}
              {oneDrive ? (
                <FormField
                  label="租户 ID"
                  htmlFor="storage-tenant"
                  error={form.formState.errors.tenantId?.message}
                  hint="留空时使用 common。"
                >
                  <Input id="storage-tenant" placeholder="common" {...form.register("tenantId")} />
                </FormField>
              ) : null}
              {oneDrive ? (
                <FormField
                  label="驱动器 ID"
                  htmlFor="storage-drive"
                  error={form.formState.errors.driveId?.message}
                  hint="SharePoint 站点驱动器；留空使用个人 OneDrive。"
                >
                  <Input id="storage-drive" placeholder="b!..." {...form.register("driveId")} />
                </FormField>
              ) : null}
              {supportsS3PathStyle(type) ? (
                <div className="flex items-center justify-between gap-4 border p-4 sm:col-span-2">
                  <div className="space-y-1">
                    <p className="text-sm font-medium">路径风格</p>
                    <p className="text-xs text-muted-foreground">
                      使用 Path-Style 访问，适用于自建 S3 兼容服务。
                    </p>
                  </div>
                  <Controller
                    control={form.control}
                    name="s3PathStyle"
                    render={({ field }) => (
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                        aria-label="路径风格"
                      />
                    )}
                  />
                </div>
              ) : null}
            </div>
          </FormDialogSection>
        ) : null}

        {requiresStoredCredentials(type) ? (
          <FormDialogSection title="访问凭据">
            {oneDrive ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField
                  label="客户端密钥"
                  htmlFor="storage-client-secret"
                  error={form.formState.errors.clientSecret?.message}
                  hint={mode === "edit" ? "留空表示保留现有密钥。" : undefined}
                >
                  <Input
                    id="storage-client-secret"
                    type="password"
                    autoComplete="new-password"
                    {...form.register("clientSecret")}
                  />
                </FormField>
                <FormField
                  label="刷新令牌"
                  htmlFor="storage-token"
                  error={form.formState.errors.token?.message}
                  hint={mode === "edit" ? "留空表示保留现有令牌。" : "可通过下方授权流程获取。"}
                >
                  <Input
                    id="storage-token"
                    type="password"
                    autoComplete="new-password"
                    {...form.register("token")}
                  />
                </FormField>
              </div>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField
                  label={type === "upyun" ? "操作员" : "Access Key"}
                  htmlFor="storage-access-key"
                  error={form.formState.errors.accessKey?.message}
                  hint={mode === "edit" ? "留空表示保留现有凭据。" : undefined}
                >
                  <Input
                    id="storage-access-key"
                    autoComplete="off"
                    {...form.register("accessKey")}
                  />
                </FormField>
                <FormField
                  label={type === "upyun" ? "操作员密码" : "Secret Key"}
                  htmlFor="storage-secret-key"
                  error={form.formState.errors.secretKey?.message}
                  hint={mode === "edit" ? "留空表示保留现有凭据。" : undefined}
                >
                  <Input
                    id="storage-secret-key"
                    type="password"
                    autoComplete="new-password"
                    {...form.register("secretKey")}
                  />
                </FormField>
              </div>
            )}
            <p className="text-xs text-muted-foreground">
              凭据仅写入不读取；编辑时留空会保留已保存的凭据。
            </p>
          </FormDialogSection>
        ) : null}

        {oneDrive ? (
          <FormDialogSection title="OneDrive 授权">
            <div className="flex flex-wrap items-center justify-between gap-3 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">
                  {oauthStatusQuery.data?.authorized ? "已授权" : "尚未授权"}
                </p>
                <p className="text-xs text-muted-foreground">
                  {oauthStatusQuery.data?.message ?? "保存策略后可通过 Microsoft 完成授权。"}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {oauthEnabled ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={oauthStatusQuery.isFetching}
                    onClick={() => void oauthStatusQuery.refetch()}
                  >
                    <RefreshCw
                      className={oauthStatusQuery.isFetching ? "animate-spin" : undefined}
                    />
                    刷新状态
                  </Button>
                ) : null}
                <Button
                  type="button"
                  size="sm"
                  disabled={!oauthEnabled || authorizeMutation.isPending}
                  onClick={() => authorizeMutation.mutate()}
                >
                  {authorizeMutation.isPending ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <ExternalLink />
                  )}
                  去授权
                </Button>
              </div>
            </div>
            <div className="space-y-2 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">解析 SharePoint 站点</p>
                <p className="text-xs text-muted-foreground">
                  输入站点地址，解析成功后自动回填驱动器 ID。
                </p>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  value={siteUrl}
                  onChange={(event) => setSiteUrl(event.target.value)}
                  placeholder="https://contoso.sharepoint.com/sites/team"
                  disabled={!oauthEnabled}
                />
                <Button
                  type="button"
                  variant="outline"
                  disabled={!oauthEnabled || !siteUrl.trim() || resolveRootMutation.isPending}
                  onClick={() => resolveRootMutation.mutate()}
                >
                  {resolveRootMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                  解析
                </Button>
              </div>
            </div>
          </FormDialogSection>
        ) : null}

        <FormDialogSection title="存储与上传">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label="文件大小限制（MB）"
              htmlFor="storage-max-size"
              error={form.formState.errors.maxSizeMb?.message}
              hint="0 表示不限制单文件大小。"
            >
              <Input id="storage-max-size" type="number" min={0} {...form.register("maxSizeMb")} />
            </FormField>
            <FormField
              label="上传分片大小（MB）"
              htmlFor="storage-chunk-size"
              error={form.formState.errors.chunkSizeMb?.message}
              hint={`0 表示使用默认值，最小值 ${minimumChunkMegabytes(type)} MB。`}
            >
              <Input
                id="storage-chunk-size"
                type="number"
                min={0}
                step={1}
                {...form.register("chunkSizeMb")}
              />
            </FormField>
          </div>
          <FormField
            label="Blob 存储目录"
            htmlFor="storage-dir-rule"
            error={form.formState.errors.dirNameRule?.message}
            hint="支持 {path}、{uid}、{randomkey8}、{date} 等魔法变量。"
          >
            <Input id="storage-dir-rule" {...form.register("dirNameRule")} />
          </FormField>
          <FormField
            label="Blob 名称"
            htmlFor="storage-file-rule"
            error={form.formState.errors.fileNameRule?.message}
            hint="需要包含至少一个随机变量，例如 {randomkey8}_{originname}。"
          >
            <Input id="storage-file-rule" {...form.register("fileNameRule")} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label="文件扩展名"
              htmlFor="storage-file-types"
              hint="留空表示不限制；多个扩展名用逗号分隔。"
            >
              <Input
                id="storage-file-types"
                placeholder="png,jpg,pdf"
                {...form.register("fileTypes")}
              />
            </FormField>
            <div className="flex items-center justify-between gap-4 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">拒绝名单</p>
                <p className="text-xs text-muted-foreground">开启后列表中的扩展名禁止上传。</p>
              </div>
              <Controller
                control={form.control}
                name="fileTypeDenyList"
                render={({ field }) => (
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    aria-label="扩展名拒绝名单"
                  />
                )}
              />
            </div>
          </div>
          <FormField
            label="文件名正则"
            htmlFor="storage-name-regexp"
            error={form.formState.errors.nameRegexp?.message}
            hint="留空表示不限制。"
          >
            <Input
              id="storage-name-regexp"
              placeholder=".*\\.pdf"
              {...form.register("nameRegexp")}
            />
          </FormField>
          <div className="flex items-center justify-between gap-4 border p-4">
            <div className="space-y-1">
              <p className="text-sm font-medium">文件名拒绝名单</p>
              <p className="text-xs text-muted-foreground">开启后匹配正则的文件名禁止上传。</p>
            </div>
            <Controller
              control={form.control}
              name="nameRegexpDenyList"
              render={({ field }) => (
                <Switch
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  aria-label="文件名拒绝名单"
                />
              )}
            />
          </div>
          {!isLocal(type) ? (
            <FormField
              label="批量删除数量"
              htmlFor="storage-delete-batch"
              error={form.formState.errors.deleteBatchSize?.message}
              hint="删除远端对象时每批处理的数量，1–1000。"
            >
              <Input
                id="storage-delete-batch"
                type="number"
                min={1}
                max={1000}
                {...form.register("deleteBatchSize")}
              />
            </FormField>
          ) : null}
        </FormDialogSection>

        {policy.status?.message ? (
          <p className="text-xs text-muted-foreground">{policy.status.message}</p>
        ) : null}
      </FormDialog>

      {corsOpen ? (
        <CorsDialog
          name={policy.metadata.name}
          onClose={() => setCorsOpen(false)}
          onSaved={() => {
            setCorsOpen(false);
            toast.success("跨域策略已提交。");
          }}
        />
      ) : null}
    </>
  );
}
