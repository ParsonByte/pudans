import { type FormEvent, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { isAxiosError } from "axios";
import { CheckCircle2, Fingerprint, Loader2, LogIn, MessageCircle } from "lucide-react";
import { toast } from "sonner";

import { IconFeishu } from "@/assets/custom/icon-feishu";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import { getPasskeyAssertion, serializeAssertion } from "../lib/webauthn";

export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passkeyLoading, setPasskeyLoading] = useState(false);

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

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
  };

  const handlePasskeyLogin = async () => {
    if (typeof window === "undefined" || !window.PublicKeyCredential) {
      toast.error("当前浏览器不支持通行密钥登录。");
      return;
    }

    setPasskeyLoading(true);
    try {
      const optionsResponse = await apiClient.passkey.passkeyAssertionOptions(
        username.trim() ? { passkeyAssertionOptionsRequest: { username: username.trim() } } : {},
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
        window.location.assign("/");
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.2em] text-muted-foreground">欢迎回来</p>
        <h2 className="text-2xl font-semibold tracking-tight">继续你的创作</h2>
        <p className="text-sm leading-6 text-muted-foreground">
          连接你的数字世界，管理所有身份与权限。
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="username">用户名或邮箱</Label>
        <Input
          id="username"
          name="username"
          autoComplete="username"
          autoCapitalize="off"
          spellCheck={false}
          placeholder="请输入用户名或邮箱"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoFocus
        />
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3">
          <Label htmlFor="password">密码</Label>
          <Link
            to="/forgot-password"
            className="text-xs font-medium text-primary transition-opacity hover:opacity-80"
          >
            忘记密码？
          </Link>
        </div>
        <PasswordInput
          id="password"
          name="password"
          autoComplete="current-password"
          placeholder="请输入密码"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
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
      </div>

      <Button type="submit" size="lg" className="mt-1 w-full">
        <LogIn />
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
          disabled={passkeyLoading}
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
          to="/legal"
          search={{ tab: "terms" }}
          className="text-primary transition-opacity hover:opacity-80"
        >
          法律协议
        </Link>
      </p>
    </form>
  );
}
