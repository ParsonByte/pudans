import { type ReactNode, useState } from "react";
import { type FieldErrors, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Loader2, RefreshCw, Save, Undo2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { FloatingActionBar } from "@/components/floating-action-bar";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

import { resolveParameterSettingError, updateParameterGroup } from "../lib/parameter-api";

type ServerFormValues = {
  tempPath: string;
  siteId: string;
  hashIdSalt: string;
  accessTokenTtlSeconds: number;
  refreshTokenTtlSeconds: number;
  gcCron: string;
};

type ServerSettingsFormProps = {
  data: Record<string, unknown>;
};

const RELATIVE_PATH_PATTERN = /^(?![\\/])(?!.*\.\.)(?!.*\/\/)[A-Za-z0-9._/-]+$/;

const DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 1800;

const DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 2_592_000;

const DEFAULT_GC_CRON = "0 0 3 * * *";

const serverSchema = z.object({
  tempPath: z
    .string()
    .trim()
    .min(1, "请输入临时路径。")
    .max(255, "临时路径不能超过 255 个字符。")
    .refine(
      (value) => value === "" || RELATIVE_PATH_PATTERN.test(value),
      "临时路径必须是不含 .. 的相对路径。",
    ),
  siteId: z.string().trim().max(64, "站点 ID 不能超过 64 个字符。"),
  hashIdSalt: z.string().trim().max(512, "HashID 盐值不能超过 512 个字符。"),
  accessTokenTtlSeconds: z
    .number({ error: "请输入有效的访问令牌有效期。" })
    .min(60, "访问令牌有效期不能小于 60 秒。")
    .max(31_536_000, "访问令牌有效期不能大于 31536000 秒。"),
  refreshTokenTtlSeconds: z
    .number({ error: "请输入有效的刷新令牌有效期。" })
    .min(60, "刷新令牌有效期不能小于 60 秒。")
    .max(31_536_000, "刷新令牌有效期不能大于 31536000 秒。"),
  gcCron: z
    .string()
    .trim()
    .max(64, "垃圾回收扫描间隔不能超过 64 个字符。")
    .refine((value) => value === "" || isCronExpression(value), "请输入六段式 Cron 表达式。"),
});

export function ServerSettingsForm({ data }: ServerSettingsFormProps) {
  const [rotateOpen, setRotateOpen] = useState(false);
  const [masterKeyConfigured, setMasterKeyConfigured] = useState(
    () => stringValue(data.masterKey).length > 0,
  );
  const [hashIdSaltConfigured, setHashIdSaltConfigured] = useState(
    () => stringValue(data.hashIdSalt).length > 0,
  );
  const form = useForm<ServerFormValues>({
    resolver: zodResolver(serverSchema) as Resolver<ServerFormValues>,
    defaultValues: readServerValues(data),
  });

  const mutation = useMutation({
    mutationFn: (values: ServerFormValues) =>
      updateParameterGroup("server", {
        tempPath: values.tempPath,
        siteId: values.siteId,
        hashIdSalt: values.hashIdSalt,
        accessTokenTtlSeconds: values.accessTokenTtlSeconds,
        refreshTokenTtlSeconds: values.refreshTokenTtlSeconds,
        gcCron: values.gcCron,
      }),
    onSuccess: ({ restartRequired }, values) => {
      setHashIdSaltConfigured(Boolean(values.hashIdSalt));
      form.reset({
        ...values,
        hashIdSalt: values.hashIdSalt ? "********" : "",
      });
      toast.success("服务器设置已保存。");

      if (restartRequired) {
        toast.info("部分安全参数需要重启后生效。");
      }
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const rotateMutation = useMutation({
    mutationFn: () => updateParameterGroup("server", { masterKey: createMasterKey() }),
    onSuccess: ({ restartRequired }) => {
      setRotateOpen(false);
      setMasterKeyConfigured(true);
      toast.success("主密钥已轮转。");

      if (restartRequired) {
        toast.info("重启服务后生效。");
      }
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    (errors: FieldErrors<ServerFormValues>) => {
      const firstMessage = Object.values(errors).find(
        (error) => typeof error?.message === "string",
      )?.message;

      toast.error(typeof firstMessage === "string" ? firstMessage : "请检查填写内容。");
    },
  );

  const formId = "server-parameter-form";
  const errors = form.formState.errors;

  return (
    <>
      <form
        id={formId}
        onSubmit={onSubmit}
        className="max-w-3xl space-y-8 md:pl-10"
        aria-busy={mutation.isPending}
        noValidate
      >
        <ServerField
          label="临时路径"
          htmlFor="server-temp-path"
          hint="存储临时文件的目录，相对于 Pudans 数据目录。修改前请确保没有正在进行的队列任务。"
          error={errors.tempPath?.message}
        >
          <Input
            id="server-temp-path"
            aria-invalid={Boolean(errors.tempPath)}
            {...form.register("tempPath")}
          />
        </ServerField>

        <ServerField
          label="站点 ID"
          htmlFor="server-site-id"
          hint="用于标识站点的唯一 ID，一般无需修改。"
          error={errors.siteId?.message}
        >
          <Input
            id="server-site-id"
            readOnly
            className={cn("bg-muted/40", "read-only:cursor-default")}
            aria-invalid={Boolean(errors.siteId)}
            {...form.register("siteId")}
          />
        </ServerField>

        <ServerField
          label="主密钥"
          hint={
            masterKeyConfigured
              ? "当前已配置主密钥。轮转后会生成新密钥并保存，所有用户令牌、签名都将失效，重启服务后生效。"
              : "当前未配置主密钥。轮转后会生成新密钥并保存，重启服务后生效。"
          }
        >
          <Button type="button" variant="secondary" size="lg" onClick={() => setRotateOpen(true)}>
            <RefreshCw />
            轮转主密钥
          </Button>
        </ServerField>

        <ServerField
          label="HashID 盐值"
          htmlFor="server-hash-id-salt"
          hint={
            hashIdSaltConfigured
              ? "用于生成 HashID 的盐值，请谨慎更改；修改后现有的直达链接、分享链接等将全部失效。保持 ******** 表示不修改。"
              : "当前未配置盐值，留空时使用系统默认值。修改后现有的直达链接、分享链接等将全部失效。"
          }
          error={errors.hashIdSalt?.message}
        >
          <PasswordInput
            id="server-hash-id-salt"
            aria-invalid={Boolean(errors.hashIdSalt)}
            {...form.register("hashIdSalt")}
          />
        </ServerField>

        <ServerField
          label="访问令牌 TTL"
          htmlFor="server-access-token-ttl"
          hint="访问令牌的有效期，单位为秒。"
          error={errors.accessTokenTtlSeconds?.message}
        >
          <Input
            id="server-access-token-ttl"
            type="number"
            min={60}
            max={31_536_000}
            step={1}
            aria-invalid={Boolean(errors.accessTokenTtlSeconds)}
            {...form.register("accessTokenTtlSeconds", { valueAsNumber: true })}
          />
        </ServerField>

        <ServerField
          label="刷新令牌 TTL"
          htmlFor="server-refresh-token-ttl"
          hint="刷新令牌的有效期，单位为秒，影响用户登录状态的保持时间。"
          error={errors.refreshTokenTtlSeconds?.message}
        >
          <Input
            id="server-refresh-token-ttl"
            type="number"
            min={60}
            max={31_536_000}
            step={1}
            aria-invalid={Boolean(errors.refreshTokenTtlSeconds)}
            {...form.register("refreshTokenTtlSeconds", { valueAsNumber: true })}
          />
        </ServerField>

        <ServerField
          label="垃圾回收扫描间隔"
          htmlFor="server-gc-cron"
          hint="设定多久扫描并回收临时文件和 KV 存储中的过期数据。填写六段式 Cron 表达式，重启服务后生效。"
          error={errors.gcCron?.message}
        >
          <Input
            id="server-gc-cron"
            aria-invalid={Boolean(errors.gcCron)}
            {...form.register("gcCron")}
          />
        </ServerField>

        <FloatingActionBar open={form.formState.isDirty}>
          <Button
            type="button"
            variant="outline"
            disabled={mutation.isPending}
            onClick={() => form.reset()}
          >
            <Undo2 />
            撤销更改
          </Button>
          <Button type="submit" form={formId} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
            保存更改
          </Button>
        </FloatingActionBar>
      </form>

      <ConfirmDialog
        open={rotateOpen}
        onOpenChange={setRotateOpen}
        title="轮转主密钥"
        desc={
          <span>
            轮转后会生成新的主密钥并保存。现有个人访问令牌、刷新令牌和签名将全部失效，需要重启服务后生效。
          </span>
        }
        confirmText="轮转主密钥"
        destructive
        isLoading={rotateMutation.isPending}
        handleConfirm={() => rotateMutation.mutate()}
      />
    </>
  );
}

type ServerFieldProps = {
  label: string;
  hint: string;
  htmlFor?: string;
  error?: string;
  children: ReactNode;
};

function ServerField({ label, hint, htmlFor, error, children }: ServerFieldProps) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor} className="text-sm font-semibold">
        {label}
      </Label>
      {children}
      <p className="text-xs leading-5 text-muted-foreground">{hint}</p>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}

function readServerValues(data: Record<string, unknown>): ServerFormValues {
  return {
    tempPath: stringValue(data.tempPath) || "tmp",
    siteId: stringValue(data.siteId),
    hashIdSalt: stringValue(data.hashIdSalt),
    accessTokenTtlSeconds: numberValue(
      data.accessTokenTtlSeconds,
      DEFAULT_ACCESS_TOKEN_TTL_SECONDS,
    ),
    refreshTokenTtlSeconds: numberValue(
      data.refreshTokenTtlSeconds,
      DEFAULT_REFRESH_TOKEN_TTL_SECONDS,
    ),
    gcCron: stringValue(data.gcCron) || DEFAULT_GC_CRON,
  };
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function isCronExpression(value: string): boolean {
  return value.trim().split(/\s+/).length === 6;
}

function createMasterKey(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  const binary = String.fromCharCode(...bytes);

  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
