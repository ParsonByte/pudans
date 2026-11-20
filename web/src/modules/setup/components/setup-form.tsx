import { type ComponentType, type ReactNode } from "react";
import { type FieldErrors, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { Globe2, Loader2, Rocket, ShieldCheck, UserRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";

import {
  type SetupFormValues,
  createSetupDefaults,
  resolveSetupError,
  setupSchema,
} from "../lib/setup-schema";

type SetupSectionProps = {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description: string;
  children: ReactNode;
};

function SetupSection({ icon: Icon, title, description, children }: SetupSectionProps) {
  return (
    <section className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="flex size-8 shrink-0 items-center justify-center bg-primary/10 text-primary">
          <Icon className="size-4" />
        </span>
        <div className="space-y-0.5">
          <h3 className="text-sm font-semibold">{title}</h3>
          <p className="text-xs leading-5 text-muted-foreground">{description}</p>
        </div>
      </div>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

type SetupFieldProps = {
  id: string;
  label: string;
  hint?: string;
  children: ReactNode;
};

function SetupField({ id, label, hint, children }: SetupFieldProps) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint ? <p className="text-xs leading-5 text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function SetupForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<SetupFormValues>({
    resolver: zodResolver(setupSchema),
    defaultValues: createSetupDefaults(),
  });

  const onSubmit = async (values: SetupFormValues) => {
    try {
      await apiClient.systemSetup.setupSystem(
        {
          setupRequest: {
            username: values.username,
            password: values.password,
            email: values.email || undefined,
            siteTitle: values.siteTitle,
            externalUrl: values.externalUrl,
          },
        },
        { timeout: 60_000 },
      );
      toast.success("系统初始化完成，请登录。");
      router.replace("/sign-in");
    } catch (error) {
      const result = resolveSetupError(error);
      toast.error(result.message);

      if (result.setupCompleted) {
        router.replace("/sign-in");
      }
    }
  };

  const onInvalid = (errors: FieldErrors<SetupFormValues>) => {
    const firstError = Object.values(errors).find((error) => error?.message);
    toast.error(firstError?.message ?? "请检查填写内容。");
  };

  return (
    <form
      onSubmit={handleSubmit(onSubmit, onInvalid)}
      className="flex flex-col gap-5"
      aria-busy={isSubmitting}
      noValidate
    >
      <div className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.2em] text-muted-foreground">首次运行</p>
        <h2 className="text-2xl font-semibold tracking-tight">初始化系统</h2>
        <p className="text-sm leading-6 text-muted-foreground">
          创建超级管理员账户，并设置站点的基础信息。
        </p>
      </div>

      <SetupSection icon={Globe2} title="站点信息" description="设置系统对外展示的名称和访问地址。">
        <div className="grid gap-4 md:grid-cols-2">
          <SetupField id="site-title" label="站点名称" hint="显示在系统页面和通知中。">
            <Input
              id="site-title"
              autoComplete="organization"
              maxLength={80}
              placeholder="例如：Pudans"
              autoFocus
              {...register("siteTitle")}
            />
          </SetupField>

          <SetupField
            id="external-url"
            label="站点地址"
            hint="用于生成站点链接，需以 http:// 或 https:// 开头。"
          >
            <Input
              id="external-url"
              type="url"
              inputMode="url"
              autoComplete="url"
              placeholder="https://example.com"
              {...register("externalUrl")}
            />
          </SetupField>
        </div>
      </SetupSection>

      <Separator />

      <SetupSection
        icon={UserRound}
        title="管理员账户"
        description="该账户将拥有系统的最高管理权限。"
      >
        <div className="grid gap-4 md:grid-cols-2">
          <SetupField
            id="username"
            label="用户名"
            hint="4–63 位，小写字母、数字、点或中划线；以字母或数字开头和结尾。"
          >
            <Input
              id="username"
              autoComplete="username"
              autoCapitalize="off"
              spellCheck={false}
              maxLength={63}
              placeholder="例如：admin"
              {...register("username")}
            />
          </SetupField>

          <SetupField id="email" label="邮箱（选填）" hint="用于接收通知和找回密码。">
            <Input
              id="email"
              type="email"
              autoComplete="email"
              autoCapitalize="off"
              spellCheck={false}
              placeholder="name@example.com"
              {...register("email")}
            />
          </SetupField>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <SetupField
            id="password"
            label="密码"
            hint="8–257 位，支持大小写字母、数字及 !@#$%^&*.?。"
          >
            <PasswordInput
              id="password"
              autoComplete="new-password"
              maxLength={257}
              placeholder="请输入管理员密码"
              {...register("password")}
            />
          </SetupField>

          <SetupField id="confirm-password" label="确认密码">
            <PasswordInput
              id="confirm-password"
              autoComplete="new-password"
              placeholder="请再次输入密码"
              {...register("confirmPassword")}
            />
          </SetupField>
        </div>
      </SetupSection>

      <p className="flex items-start gap-2 text-xs leading-5 text-muted-foreground">
        <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-primary" />
        初始化完成后，使用该账户登录。
      </p>

      <Button type="submit" size="lg" className="mt-1 w-full" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="animate-spin" /> : <Rocket />}
        完成初始化
      </Button>
    </form>
  );
}
