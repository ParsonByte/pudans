import { type FormEvent, Fragment, useEffect, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";
import { ArrowLeft, ArrowRight, Check, CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { z } from "zod";

import { FormField } from "@/components/form-field";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { AuthFormCard } from "@/modules/auth/components/auth-form-card";
import { type CaptchaChallengeState, CaptchaField } from "@/modules/auth/components/captcha-field";
import { resolveAuthError } from "@/modules/auth/lib/auth-error";

type RecoveryStep = 1 | 2 | 3;

const CODE_PATTERN = /^\d{6}$/;
const STEPS = [
  { number: 1, label: "验证邮箱" },
  { number: 2, label: "设置密码" },
  { number: 3, label: "完成" },
] as const;

const recoverySchema = z
  .object({
    email: z
      .string()
      .trim()
      .min(1, "请输入绑定邮箱。")
      .refine((value) => z.email().safeParse(value).success, "请输入有效的邮箱地址。"),
    code: z.string().trim().regex(CODE_PATTERN, "请输入 6 位邮箱验证码。"),
    newPassword: z.string().min(8, "新密码至少需要 8 个字符。"),
    confirmPassword: z.string().min(1, "请再次输入新密码。"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "两次输入的密码不一致。",
    path: ["confirmPassword"],
  });

type RecoveryValues = z.infer<typeof recoverySchema>;

function recoveryErrorMessage(error: unknown): string {
  return resolveAuthError(
    error,
    {
      400: "验证码无效或已过期，请重新获取。",
      429: "验证码发送过于频繁，请稍后再试。",
      503: "邮件服务暂不可用，请联系实例管理员。",
    },
    "操作失败，请稍后重试。",
  );
}

type RecoveryErrorPayload = {
  code?: string;
};

function isRecoveryCaptchaError(error: unknown): boolean {
  return (
    isAxiosError<RecoveryErrorPayload>(error) &&
    error.response?.status === 400 &&
    error.response.data?.code === "recovery-captcha-invalid"
  );
}

export function ForgotPasswordForm() {
  const [step, setStep] = useState<RecoveryStep>(1);
  const [codeSent, setCodeSent] = useState(false);
  const [codeSending, setCodeSending] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaChallengeState | null>(null);
  const [captchaAnswer, setCaptchaAnswer] = useState("");
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const form = useForm<RecoveryValues>({
    resolver: zodResolver(recoverySchema) as Resolver<RecoveryValues>,
    defaultValues: { email: "", code: "", newPassword: "", confirmPassword: "" },
  });
  const errors = form.formState.errors;
  const submitting = form.formState.isSubmitting;

  const loadCaptcha = async () => {
    setCaptchaLoading(true);
    try {
      const response = await apiClient.captcha.getCaptchaChallenge();
      const challenge = response.data as CaptchaChallengeState;
      if (challenge.challengeId && challenge.image) {
        setCaptcha(challenge);
        setCaptchaAnswer("");
      } else {
        setCaptcha(null);
      }
    } catch {
      toast.error("验证码加载失败，请稍后重试。");
    } finally {
      setCaptchaLoading(false);
    }
  };

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void loadCaptcha();
    }, 0);
    return () => window.clearTimeout(handle);
  }, []);

  const captchaPayload = () =>
    captcha ? { captchaChallengeId: captcha.challengeId, captchaAnswer: captchaAnswer.trim() } : {};

  const sendCode = async () => {
    if (!(await form.trigger("email"))) {
      return;
    }
    if (captcha && !captchaAnswer.trim()) {
      toast.error("请输入图片验证码。");
      return;
    }

    setCodeSending(true);
    try {
      await apiClient.passwordRecovery.sendPasswordRecoveryCode({
        sendCodeRequest: {
          email: form.getValues("email").trim(),
          ...captchaPayload(),
        },
      });
      setCodeSent(true);
      toast.success("验证码已发送。");
      if (captcha) {
        void loadCaptcha();
      }
    } catch (requestError) {
      if (isRecoveryCaptchaError(requestError)) {
        await loadCaptcha();
        toast.error("请输入图片验证码后重试。");
        return;
      }
      toast.error(recoveryErrorMessage(requestError));
    } finally {
      setCodeSending(false);
    }
  };

  const submitRecovery = form.handleSubmit(async (values) => {
    if (captcha && !captchaAnswer.trim()) {
      toast.error("请输入图片验证码。");
      return;
    }
    try {
      await apiClient.passwordRecovery.resetPasswordWithRecoveryCode({
        resetPasswordRequest: {
          email: values.email,
          code: values.code,
          newPassword: values.newPassword,
          ...captchaPayload(),
        },
      });
      setStep(3);
      toast.success("密码已重置。");
    } catch (requestError) {
      if (isRecoveryCaptchaError(requestError)) {
        await loadCaptcha();
        toast.error("请输入图片验证码后重试。");
        return;
      }
      toast.error(recoveryErrorMessage(requestError));
    }
  });

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (step === 1) {
      if (await form.trigger(["email", "code"])) {
        setStep(2);
      }
      return;
    }

    await submitRecovery(event);
  };

  return (
    <AuthFormCard
      eyebrow="找回账号"
      title="找回密码"
      description={
        step === 2
          ? "设置一个新的登录密码，至少 8 个字符。"
          : step === 3
            ? "现在可以使用新密码登录。"
            : "获取邮箱验证码，确认这是你的账号。"
      }
    >
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
              <FormField htmlFor="recovery-email" label="绑定邮箱" error={errors.email?.message}>
                <Input
                  id="recovery-email"
                  type="email"
                  autoComplete="email"
                  autoCapitalize="off"
                  spellCheck={false}
                  placeholder="请输入绑定邮箱"
                  autoFocus
                  {...form.register("email")}
                />
              </FormField>

              <FormField htmlFor="recovery-code" label="邮箱验证码" error={errors.code?.message}>
                <div className="flex items-center gap-2">
                  <Input
                    id="recovery-code"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="请输入验证码"
                    {...form.register("code")}
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
              </FormField>

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
              <FormField
                htmlFor="recovery-password"
                label="新密码"
                error={errors.newPassword?.message}
              >
                <PasswordInput
                  id="recovery-password"
                  autoComplete="new-password"
                  placeholder="至少 8 个字符"
                  autoFocus
                  {...form.register("newPassword")}
                />
              </FormField>

              <FormField
                htmlFor="recovery-confirm-password"
                label="确认密码"
                error={errors.confirmPassword?.message}
              >
                <PasswordInput
                  id="recovery-confirm-password"
                  autoComplete="new-password"
                  placeholder="请再次输入新密码"
                  {...form.register("confirmPassword")}
                />
              </FormField>

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

          {captcha ? (
            <CaptchaField
              challenge={captcha}
              answer={captchaAnswer}
              onAnswerChange={setCaptchaAnswer}
              onRefresh={() => {
                void loadCaptcha();
              }}
              refreshing={captchaLoading}
            />
          ) : null}

          <Button
            type="submit"
            size="lg"
            className="mt-1 w-full"
            disabled={submitting || captchaLoading}
          >
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
    </AuthFormCard>
  );
}
