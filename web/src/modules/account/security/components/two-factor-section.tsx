import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Loader2 } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { toast } from "sonner";

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

export function TwoFactorSection() {
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: TWO_FACTOR_QUERY_KEY,
    queryFn: async () => (await apiClient.twoFactorAuth.getTwoFactorAuthenticationSettings()).data,
  });
  const settings = settingsQuery.data;
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [secret, setSecret] = useState("");
  const [authLink, setAuthLink] = useState("");

  const refresh = () => queryClient.invalidateQueries({ queryKey: TWO_FACTOR_QUERY_KEY });

  const linkMutation = useMutation({
    mutationFn: async () => (await apiClient.twoFactorAuth.getTotpAuthLink()).data,
    onSuccess: (data) => {
      setSecret(data.rawSecret ?? "");
      setAuthLink(data.authLink ?? "");
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const configureMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.twoFactorAuth.configurerTotp({ totpRequest: { secret, code, password } }))
        .data,
    onSuccess: () => {
      toast.success("TOTP 已配置，请稍候输入新的验证码以启用双重验证。");
      setCode("");
      setPassword("");
      setSecret("");
      setAuthLink("");
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const enableMutation = useMutation({
    mutationFn: async () => {
      const passwordRequest = { password, totpCode: code };
      return (await apiClient.twoFactorAuth.enableTwoFactor({ passwordRequest })).data;
    },
    onSuccess: () => {
      toast.success("双重验证已启用。");
      setCode("");
      setPassword("");
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const disableMutation = useMutation({
    mutationFn: async () => {
      const passwordRequest = { password, totpCode: code };
      return (await apiClient.twoFactorAuth.disableTwoFactor({ passwordRequest })).data;
    },
    onSuccess: () => {
      toast.success("双重验证已停用。");
      setCode("");
      setPassword("");
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const enabled = Boolean(settings?.enabled);
  const totpConfigured = Boolean(settings?.totpConfigured);

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
      <div className="grid max-w-xl gap-4 p-5">
        {enabled ? (
          <>
            <p className="text-sm leading-6 text-muted-foreground">
              双重验证已启用。停用需要输入当前密码和身份验证器中的验证码。
            </p>
            <div className="space-y-2">
              <Label htmlFor="disable-password">当前密码</Label>
              <PasswordInput
                id="disable-password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="disable-code">验证码</Label>
              <Input
                id="disable-code"
                inputMode="numeric"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="6 位验证码"
              />
            </div>
            <div className="flex justify-end">
              <Button
                variant="destructive"
                onClick={() => disableMutation.mutate()}
                disabled={disableMutation.isPending}
              >
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
            <div className="space-y-2">
              <Label htmlFor="enable-password">当前密码</Label>
              <PasswordInput
                id="enable-password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="enable-code">验证码</Label>
              <Input
                id="enable-code"
                inputMode="numeric"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="请输入身份验证器中的新验证码"
              />
            </div>
            <div className="flex justify-end">
              <Button onClick={() => enableMutation.mutate()} disabled={enableMutation.isPending}>
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
                <div className="rounded-lg bg-white p-3">
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
            <div className="space-y-2">
              <Label htmlFor="setup-code">验证码</Label>
              <Input
                id="setup-code"
                inputMode="numeric"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="请输入身份验证器中的验证码"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="setup-password">当前密码</Label>
              <PasswordInput
                id="setup-password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            <div className="flex justify-end">
              <Button
                onClick={() => configureMutation.mutate()}
                disabled={configureMutation.isPending || !secret}
              >
                {configureMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                完成配置
              </Button>
            </div>
          </>
        )}
      </div>
    </ConsoleSection>
  );
}
