import { useEffect, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient, axiosInstance } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import {
  CheckCircle2,
  Fingerprint,
  Loader2,
  LogIn,
  MessageCircle,
  TriangleAlert,
} from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { z } from "zod";

import { IconFeishu } from "@/assets/custom/icon-feishu";
import { FormField } from "@/components/form-field";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getPasskeyAssertion, serializeAssertion } from "@/lib/webauthn";
import { AuthFormCard } from "@/modules/auth/components/auth-form-card";
import { resolveAuthError } from "@/modules/auth/lib/auth-error";
import { resolvePostLoginRedirect } from "@/modules/auth/lib/post-login-redirect";
import { getLoginSession } from "@/modules/auth/lib/session";

import { encryptLoginPassword } from "../lib/login-crypto";

type LoginFormProps = {
  redirectTo?: string;
  errorMessage?: string;
};

const loginSchema = z.object({
  username: z.string().trim().min(1, "请输入用户名或邮箱。"),
  password: z.string().min(1, "请输入密码。"),
});

type LoginValues = z.infer<typeof loginSchema>;

function loginErrorMessage(error: unknown): string {
  return resolveAuthError(
    error,
    {
      400: "登录请求无效，请检查填写内容。",
      401: "用户名或密码错误。",
      429: "尝试次数过多，请稍后重试。",
    },
    "登录失败，请稍后重试。",
  );
}

export function LoginForm({ redirectTo, errorMessage }: LoginFormProps) {
  const [passkeyLoading, setPasskeyLoading] = useState(false);
  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema) as Resolver<LoginValues>,
    defaultValues: { username: "", password: "" },
  });
  const errors = form.formState.errors;
  const submitting = form.formState.isSubmitting;

  useEffect(() => {
    if (errorMessage) {
      toast.error(errorMessage, { id: "login-error" });
    }
  }, [errorMessage]);

  const providersQuery = useQuery({
    queryKey: ["login-providers"],
    queryFn: async () => (await apiClient.login.listLoginProviders()).data,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  const providers = providersQuery.data ?? [];
  const feishuUrl = providers.find(
    (provider) => provider.name?.toLowerCase() === "feishu",
  )?.authenticationUrl;
  const otherProviders = providers.filter(
    (provider) => provider.name?.toLowerCase() !== "feishu" && Boolean(provider.authenticationUrl),
  );

  const loginStatusQuery = useQuery({
    queryKey: ["login-status"],
    queryFn: async () => (await apiClient.login.getLoginStatus()).data,
    retry: false,
    staleTime: 0,
  });
  const rsaEncryptionEnabled =
    (loginStatusQuery.data as { rsaEncryptionEnabled?: boolean } | undefined)
      ?.rsaEncryptionEnabled ?? true;

  const onSubmit = async (values: LoginValues) => {
    try {
      let encryptPassword = rsaEncryptionEnabled;
      if (!loginStatusQuery.data) {
        const refreshed = await loginStatusQuery.refetch();
        encryptPassword =
          (refreshed.data as { rsaEncryptionEnabled?: boolean } | undefined)
            ?.rsaEncryptionEnabled ?? true;
      }
      const submittedPassword = encryptPassword
        ? await encryptLoginPassword(values.password)
        : values.password;
      const response = await axiosInstance.post<{ twoFactorRequired?: boolean }>(
        "/login",
        new URLSearchParams({ username: values.username, password: submittedPassword }),
        { headers: { "Content-Type": "application/x-www-form-urlencoded" } },
      );

      if (response.data?.twoFactorRequired) {
        toast.error("该账号需要完成两步验证。");
        return;
      }

      const session = await getLoginSession();

      if (!session.authenticated) {
        toast.error("登录状态未生效，请重试。");
        return;
      }

      if (session.consoleAccessDisallowed) {
        window.location.assign("/403");
        return;
      }

      window.location.assign(resolvePostLoginRedirect(redirectTo, session.redirectUrl));
    } catch (requestError) {
      toast.error(loginErrorMessage(requestError));
    }
  };

  const handlePasskeyLogin = async () => {
    if (typeof window === "undefined" || !window.PublicKeyCredential) {
      toast.error("当前浏览器不支持通行密钥登录。");
      return;
    }

    const username = form.getValues("username").trim();

    setPasskeyLoading(true);
    try {
      const optionsResponse = await apiClient.passkey.passkeyAssertionOptions(
        username ? { passkeyAssertionOptionsRequest: { username } } : {},
      );
      const publicKey = optionsResponse.data.publicKey;

      if (!publicKey) {
        throw new Error("Missing passkey assertion options.");
      }

      const credential = await getPasskeyAssertion(publicKey as Record<string, unknown>);
      const result = await apiClient.passkey.passkeyAssertionVerify({
        body: serializeAssertion(credential),
      });

      if (result.data.authenticated) {
        const session = await getLoginSession();

        if (!session.authenticated) {
          toast.error("登录状态未生效，请重试。");
          return;
        }

        if (session.consoleAccessDisallowed) {
          window.location.assign("/403");
          return;
        }

        window.location.assign(resolvePostLoginRedirect(redirectTo, session.redirectUrl));
        return;
      }

      if (result.data.twoFactorRequired) {
        toast.error("该账号需要完成两步验证，请使用密码登录。");
        return;
      }

      toast.error("通行密钥登录失败，请重试。");
    } catch (requestError) {
      if (isAxiosError(requestError) && requestError.response?.status === 403) {
        toast.error("通行密钥登录未启用。");
      } else if (requestError instanceof DOMException && requestError.name === "NotAllowedError") {
        toast.info("已取消通行密钥登录。");
      } else {
        toast.error("通行密钥登录失败，请重试。");
      }
    } finally {
      setPasskeyLoading(false);
    }
  };

  const handleFeishuLogin = () => {
    if (feishuUrl) {
      window.location.assign(feishuUrl);
      return;
    }

    toast.error("飞书未配置");
  };

  return (
    <AuthFormCard eyebrow="欢迎回来" title="继续你的创作" description="连接你的数字空间。">
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <FormField htmlFor="username" label="用户名或邮箱" error={errors.username?.message}>
          <Input
            id="username"
            autoComplete="username"
            autoCapitalize="off"
            spellCheck={false}
            placeholder="请输入用户名或邮箱"
            autoFocus
            {...form.register("username")}
          />
        </FormField>

        <div className="space-y-2">
          <div className="flex items-center justify-between gap-3">
            <Label htmlFor="password">密码</Label>
            <Link
              href="/forgot-password"
              className="text-xs font-medium text-primary transition-opacity hover:opacity-80"
            >
              忘记密码？
            </Link>
          </div>
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="请输入密码"
            {...form.register("password")}
          />
          {errors.password?.message ? (
            <p className="text-xs text-destructive">{errors.password.message}</p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
          <p className="flex items-center gap-2">
            <CheckCircle2 className="size-3.5 text-primary" />
            登录状态将安全保留
          </p>
          <p className="flex items-center gap-2">
            <CheckCircle2 className="size-3.5 text-primary" />
            新设备登录会提醒你
          </p>
          {!rsaEncryptionEnabled ? (
            <p className="flex items-center gap-2 text-warning">
              <TriangleAlert className="size-3.5" />
              当前实例未启用登录传输加密，请确认通过 HTTPS 访问
            </p>
          ) : null}
        </div>

        <Button type="submit" size="lg" className="mt-1 w-full" disabled={submitting}>
          {submitting ? <Loader2 className="animate-spin" /> : <LogIn />}
          登录并继续
        </Button>

        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="h-px flex-1 bg-border" />
          或使用其他方式
          <span className="h-px flex-1 bg-border" />
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="flex-1 justify-center"
            disabled={passkeyLoading || submitting}
            onClick={() => {
              void handlePasskeyLogin();
            }}
          >
            {passkeyLoading ? <Loader2 className="animate-spin" /> : <Fingerprint />}
            通行密钥
          </Button>

          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-9 w-20 shrink-0"
            aria-label="飞书"
            title="飞书"
            onClick={handleFeishuLogin}
          >
            <IconFeishu size={20} className="size-5 shrink-0" />
          </Button>

          {otherProviders.map((provider, index) => (
            <Button
              key={`${provider.name}-${provider.authenticationUrl}-${index}`}
              asChild
              variant="outline"
              size="icon"
              className="size-9 shrink-0"
            >
              <a
                href={provider.authenticationUrl}
                aria-label={provider.displayName ?? provider.name ?? "外部登录"}
                title={provider.displayName ?? provider.name ?? "外部登录"}
              >
                {provider.logo ? (
                  <img src={provider.logo} alt="" className="size-4 shrink-0" />
                ) : (
                  <MessageCircle />
                )}
              </a>
            </Button>
          ))}
        </div>

        <p className="mt-2 text-center text-xs leading-5 text-muted-foreground">
          登录即代表同意{" "}
          <Link
            href="/legal?tab=terms"
            className="text-primary transition-opacity hover:opacity-80"
          >
            法律协议
          </Link>
        </p>
      </form>
    </AuthFormCard>
  );
}
