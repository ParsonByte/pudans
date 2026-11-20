import { type FormEvent, Fragment, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";
import { ArrowLeft, ArrowRight, Check, CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";

import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

type RecoveryStep = 1 | 2 | 3;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_PATTERN = /^\d{6}$/;
const STEPS = [
  { number: 1, label: "验证邮箱" },
  { number: 2, label: "设置密码" },
  { number: 3, label: "完成" },
] as const;

function recoveryErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    if (error.response?.status === 429) {
      return "验证码发送过于频繁，请稍后再试。";
    }
    if (error.response?.status === 503) {
      return "邮件服务暂不可用，请联系实例管理员。";
    }
    if (error.response?.status === 400) {
      return "验证码无效或已过期，请重新获取。";
    }
  }

  return "操作失败，请稍后重试。";
}

export function ForgotPasswordForm() {
  const [step, setStep] = useState<RecoveryStep>(1);
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [codeSending, setCodeSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const sendCode = async () => {
    if (!EMAIL_PATTERN.test(email.trim())) {
      toast.error("请输入有效的邮箱地址。");
      return;
    }

    setCodeSending(true);
    try {
      await apiClient.passwordRecovery.sendPasswordRecoveryCode({
        sendCodeRequest: { email: email.trim() },
      });
      setCodeSent(true);
      toast.success("验证码已发送。");
    } catch (requestError) {
      toast.error(recoveryErrorMessage(requestError));
    } finally {
      setCodeSending(false);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (step === 1) {
      if (!EMAIL_PATTERN.test(email.trim())) {
        toast.error("请输入有效的邮箱地址。");
        return;
      }
      if (!CODE_PATTERN.test(code.trim())) {
        toast.error("请输入 6 位邮箱验证码。");
        return;
      }
      setStep(2);
      return;
    }

    if (step === 2) {
      if (newPassword.length < 8) {
        toast.error("新密码至少需要 8 个字符。");
        return;
      }
      if (newPassword !== confirmPassword) {
        toast.error("两次输入的密码不一致。");
        return;
      }

      setSubmitting(true);
      try {
        await apiClient.passwordRecovery.resetPasswordWithRecoveryCode({
          resetPasswordRequest: {
            email: email.trim(),
            code: code.trim(),
            newPassword,
          },
        });
        setStep(3);
        toast.success("密码已重置。");
      } catch (requestError) {
        toast.error(recoveryErrorMessage(requestError));
      } finally {
        setSubmitting(false);
      }
    }
  };

  return (
    <div className="flex flex-1 flex-col gap-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.2em] text-muted-foreground">找回账号</p>
        <h2 className="text-2xl font-semibold tracking-tight">找回密码</h2>
        <p className="text-sm leading-6 text-muted-foreground">
          {step === 2
            ? "设置一个新的登录密码，至少 8 个字符。"
            : step === 3
              ? "现在可以使用新密码登录。"
              : "获取邮箱验证码，确认这是你的账号。"}
        </p>
      </div>

      <div className="flex items-center gap-2" aria-label="找回密码步骤">
        {STEPS.map((item, index) => (
          <Fragment key={item.number}>
            {index > 0 ? (
              <span
                className={cn("h-px flex-1 bg-border", step > index && "bg-primary")}
                aria-hidden="true"
              />
            ) : null}
            <div
              className={cn(
                "flex items-center gap-1.5 text-xs",
                step === item.number ? "font-medium text-foreground" : "text-muted-foreground",
              )}
              aria-current={step === item.number ? "step" : undefined}
            >
              <span
                className={cn(
                  "inline-flex size-6 items-center justify-center border text-[11px]",
                  step === item.number && "border-primary bg-primary text-primary-foreground",
                  step > item.number && "border-primary bg-primary/10 text-primary",
                )}
              >
                {step > item.number ? <Check className="size-3" /> : item.number}
              </span>
              <span className="hidden sm:inline">{item.label}</span>
            </div>
          </Fragment>
        ))}
      </div>

      {step === 3 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-8 text-center">
          <span className="inline-flex size-14 items-center justify-center bg-primary/10 text-primary">
            <CheckCircle2 className="size-8" />
          </span>
          <h3 className="text-lg font-semibold">密码已重置</h3>
          <p className="text-sm leading-6 text-muted-foreground">现在可以使用新密码登录。</p>
          <Button asChild className="mt-2">
            <Link href="/sign-in">返回登录</Link>
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-1 flex-col gap-4" noValidate>
          {step === 1 ? (
            <>
              <div className="space-y-2">
                <Label htmlFor="recovery-email">绑定邮箱</Label>
                <Input
                  id="recovery-email"
                  type="email"
                  name="email"
                  autoComplete="email"
                  autoCapitalize="off"
                  spellCheck={false}
                  placeholder="请输入绑定邮箱"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  autoFocus
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="recovery-code">邮箱验证码</Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="recovery-code"
                    name="code"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="请输入验证码"
                    value={code}
                    onChange={(event) => setCode(event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    className="shrink-0"
                    disabled={codeSending}
                    onClick={() => {
                      void sendCode();
                    }}
                  >
                    {codeSending ? "发送中…" : codeSent ? "重新获取" : "获取验证码"}
                  </Button>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
                <p className="flex items-center gap-2">
                  <ShieldCheck className="size-3.5 text-primary" />
                  验证码仅用于身份确认
                </p>
                <p className="flex items-center gap-2">
                  <ShieldCheck className="size-3.5 text-primary" />
                  不会公开邮箱状态
                </p>
              </div>
            </>
          ) : (
            <>
              <div className="space-y-2">
                <Label htmlFor="recovery-password">新密码</Label>
                <PasswordInput
                  id="recovery-password"
                  name="new-password"
                  autoComplete="new-password"
                  placeholder="至少 8 个字符"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  autoFocus
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="recovery-confirm-password">确认密码</Label>
                <PasswordInput
                  id="recovery-confirm-password"
                  name="confirm-password"
                  autoComplete="new-password"
                  placeholder="请再次输入新密码"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                />
              </div>

              <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
                <p className="flex items-center gap-2">
                  <ShieldCheck className="size-3.5 text-primary" />
                  至少 8 个字符
                </p>
                <p className="flex items-center gap-2">
                  <ShieldCheck className="size-3.5 text-primary" />
                  请勿与其他站点共用密码
                </p>
              </div>
            </>
          )}

          <Button type="submit" size="lg" className="mt-1 w-full" disabled={submitting}>
            {submitting ? <Loader2 className="animate-spin" /> : <ArrowRight />}
            {step === 2 ? "重置密码" : "下一步"}
          </Button>
        </form>
      )}

      <Link
        href="/sign-in"
        className="mt-auto inline-flex items-center justify-center gap-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" />
        返回登录
      </Link>
    </div>
  );
}
