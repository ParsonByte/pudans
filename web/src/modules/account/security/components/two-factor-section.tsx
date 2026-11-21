import { useState } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Loader2 } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { toast } from "sonner";
import { z } from "zod";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
import { PasswordInput } from "@/components/password-input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

const TWO_FACTOR_QUERY_KEY = ["account-two-factor"];

const ERROR_MESSAGES = {
  notFound: "用户不存在。",
  conflict: "操作失败，请稍后重试。",
};

const twoFactorSchema = z.object({
  password: z.string().min(1, "请输入当前密码。"),
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, "请输入 6 位验证码。"),
});

type TwoFactorValues = z.infer<typeof twoFactorSchema>;

export function TwoFactorSection() {
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: TWO_FACTOR_QUERY_KEY,
    queryFn: async () => (await apiClient.twoFactorAuth.getTwoFactorAuthenticationSettings()).data,
  });
  const settings = settingsQuery.data;
  const [secret, setSecret] = useState("");
  const [authLink, setAuthLink] = useState("");
  const [removeOpen, setRemoveOpen] = useState(false);
  const [removePassword, setRemovePassword] = useState("");
  const [removeCode, setRemoveCode] = useState("");
  const form = useForm<TwoFactorValues>({
    resolver: zodResolver(twoFactorSchema) as Resolver<TwoFactorValues>,
    defaultValues: { password: "", code: "" },
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: TWO_FACTOR_QUERY_KEY });
  const clearForm = () => {
    form.reset();
    setSecret("");
    setAuthLink("");
  };

  const linkMutation = useMutation({
    mutationFn: async () => (await apiClient.twoFactorAuth.getTotpAuthLink()).data,
    onSuccess: (data) => {
      setSecret(data.rawSecret ?? "");
      setAuthLink(data.authLink ?? "");
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const configureMutation = useMutation({
    mutationFn: async (values: TwoFactorValues) =>
      (
        await apiClient.twoFactorAuth.configurerTotp({
          totpRequest: { secret, code: values.code, password: values.password },
        })
      ).data,
    onSuccess: () => {
      toast.success("TOTP 已配置，请稍候输入新的验证码以启用双重验证。");
      clearForm();
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const enableMutation = useMutation({
    mutationFn: async (values: TwoFactorValues) => {
      const passwordRequest = { password: values.password, totpCode: values.code };
      return (await apiClient.twoFactorAuth.enableTwoFactor({ passwordRequest })).data;
    },
    onSuccess: () => {
      toast.success("双重验证已启用。");
      clearForm();
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const disableMutation = useMutation({
    mutationFn: async (values: TwoFactorValues) => {
      const passwordRequest = { password: values.password, totpCode: values.code };
      return (await apiClient.twoFactorAuth.disableTwoFactor({ passwordRequest })).data;
    },
    onSuccess: () => {
      toast.success("双重验证已停用。");
      clearForm();
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const removeTotpMutation = useMutation({
    mutationFn: async () => {
      const passwordRequest = { password: removePassword, totpCode: removeCode };
      return (await apiClient.twoFactorAuth.deleteTotp({ passwordRequest })).data;
    },
    onSuccess: () => {
      toast.success("身份验证器已移除。");
      setRemoveOpen(false);
      setRemovePassword("");
      setRemoveCode("");
      clearForm();
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const enabled = Boolean(settings?.enabled);
  const totpConfigured = Boolean(settings?.totpConfigured);
  const errors = form.formState.errors;

  const confirmRemoveTotp = () => {
    if (!removePassword.trim()) {
      toast.error("请输入当前密码。");
      return;
    }
    if (!/^\d{6}$/.test(removeCode.trim())) {
      toast.error("请输入 6 位验证码。");
      return;
    }
    removeTotpMutation.mutate();
  };

  const submit = form.handleSubmit((values) => {
    if (enabled) {
      disableMutation.mutate(values);
      return;
    }
    if (totpConfigured) {
      enableMutation.mutate(values);
      return;
    }
    configureMutation.mutate(values);
  });

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="双重验证"
        description="使用身份验证器生成的一次性验证码保护账号。"
        action={
          <Badge variant={enabled ? "default" : "secondary"}>
            {enabled ? "已启用" : totpConfigured ? "待启用" : "未启用"}
          </Badge>
        }
      />
      <form className="grid max-w-xl gap-4 p-5" onSubmit={submit} noValidate>
        {enabled ? (
          <>
            <p className="text-sm leading-6 text-muted-foreground">
              双重验证已启用。停用需要输入当前密码和身份验证器中的验证码；停用后可移除身份验证器。
            </p>
            <FormField htmlFor="disable-password" label="当前密码" error={errors.password?.message}>
              <PasswordInput
                id="disable-password"
                autoComplete="current-password"
                {...form.register("password")}
              />
            </FormField>
            <FormField htmlFor="disable-code" label="验证码" error={errors.code?.message}>
              <Input
                id="disable-code"
                inputMode="numeric"
                placeholder="6 位验证码"
                {...form.register("code")}
              />
            </FormField>
            <div className="flex justify-end">
              <Button type="submit" variant="destructive" disabled={disableMutation.isPending}>
                {disableMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                停用双重验证
              </Button>
            </div>
          </>
        ) : totpConfigured ? (
          <>
            <p className="text-sm leading-6 text-muted-foreground">
              身份验证器已配置。输入当前密码和新的验证码即可启用双重验证。
            </p>
            <FormField htmlFor="enable-password" label="当前密码" error={errors.password?.message}>
              <PasswordInput
                id="enable-password"
                autoComplete="current-password"
                {...form.register("password")}
              />
            </FormField>
            <FormField htmlFor="enable-code" label="验证码" error={errors.code?.message}>
              <Input
                id="enable-code"
                inputMode="numeric"
                placeholder="请输入身份验证器中的新验证码"
                {...form.register("code")}
              />
            </FormField>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={removeTotpMutation.isPending}
                onClick={() => setRemoveOpen(true)}
              >
                移除身份验证器
              </Button>
              <Button type="submit" disabled={enableMutation.isPending}>
                {enableMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                启用双重验证
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-sm leading-6 text-muted-foreground">
              生成密钥后，在身份验证器中手动添加或粘贴 otpauth 链接，然后输入验证码完成配置。
            </p>
            <div>
              <Button
                type="button"
                variant="outline"
                onClick={() => linkMutation.mutate()}
                disabled={linkMutation.isPending}
              >
                {linkMutation.isPending ? <Loader2 className="animate-spin" /> : <KeyRound />}
                生成 TOTP 密钥
              </Button>
            </div>
            {authLink ? (
              <div className="flex flex-wrap items-start gap-4">
                <div className="bg-card p-3">
                  <QRCodeSVG
                    value={authLink}
                    size={176}
                    level="M"
                    bgColor="#FFFFFF"
                    fgColor="#000000"
                  />
                </div>
                <div className="min-w-0 flex-1 space-y-2">
                  <Label>密钥</Label>
                  <Input readOnly value={secret} className="font-mono" />
                  <Label>otpauth 链接</Label>
                  <Input readOnly value={authLink} className="font-mono text-xs" />
                </div>
              </div>
            ) : null}
            <FormField htmlFor="setup-code" label="验证码" error={errors.code?.message}>
              <Input
                id="setup-code"
                inputMode="numeric"
                placeholder="请输入身份验证器中的验证码"
                {...form.register("code")}
              />
            </FormField>
            <FormField htmlFor="setup-password" label="当前密码" error={errors.password?.message}>
              <PasswordInput
                id="setup-password"
                autoComplete="current-password"
                {...form.register("password")}
              />
            </FormField>
            <div className="flex justify-end">
              <Button type="submit" disabled={configureMutation.isPending || !secret}>
                {configureMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                完成配置
              </Button>
            </div>
          </>
        )}
      </form>
      <ConfirmDialog
        open={removeOpen}
        onOpenChange={(open) => {
          if (!open) {
            setRemoveOpen(false);
            setRemovePassword("");
            setRemoveCode("");
          }
        }}
        title="移除身份验证器"
        desc="移除后身份验证器中的密钥失效；如需继续使用双重验证，请重新生成密钥并完成配置。请输入当前密码和身份验证器中的验证码。"
        destructive
        confirmText="移除"
        isLoading={removeTotpMutation.isPending}
        handleConfirm={confirmRemoveTotp}
      >
        <div className="grid gap-3">
          <FormField htmlFor="remove-totp-password" label="当前密码">
            <PasswordInput
              id="remove-totp-password"
              autoComplete="current-password"
              value={removePassword}
              onChange={(event) => setRemovePassword(event.target.value)}
            />
          </FormField>
          <FormField htmlFor="remove-totp-code" label="验证码">
            <Input
              id="remove-totp-code"
              inputMode="numeric"
              placeholder="6 位验证码"
              value={removeCode}
              onChange={(event) => setRemoveCode(event.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>
    </ConsoleSection>
  );
}
