import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormField } from "@/components/form-field";
import { PasswordInput } from "@/components/password-input";
import { useSudo } from "@/components/sudo/sudo-provider";
import { Button } from "@/components/ui/button";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

const ERROR_MESSAGES = {
  notFound: "用户不存在。",
  conflict: "密码修改失败，请稍后重试。",
};

const passwordSchema = z
  .object({
    oldPassword: z.string().min(1, "请输入当前密码。"),
    password: z.string().min(8, "新密码至少需要 8 位。"),
    confirmation: z.string().min(1, "请再次输入新密码。"),
  })
  .refine((values) => values.password === values.confirmation, {
    message: "两次输入的新密码不一致。",
    path: ["confirmation"],
  });

type PasswordValues = z.infer<typeof passwordSchema>;

export function PasswordSection() {
  const { requireSudo } = useSudo();
  const form = useForm<PasswordValues>({
    resolver: zodResolver(passwordSchema) as Resolver<PasswordValues>,
    defaultValues: { oldPassword: "", password: "", confirmation: "" },
  });

  const mutation = useMutation({
    mutationFn: async (values: PasswordValues) => {
      await requireSudo();
      return (
        await apiClient.user.changeMySelfPassword({
          changeMyPasswordRequest: { oldPassword: values.oldPassword, password: values.password },
        })
      ).data;
    },
    onSuccess: () => {
      toast.success("密码已更新。");
      form.reset();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const errors = form.formState.errors;

  return (
    <ConsoleSection>
      <ConsoleSectionHeader title="修改密码" description="修改后需要重新使用新密码登录。" />
      <form
        className="grid max-w-xl gap-4 p-5"
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <FormField htmlFor="current-password" label="当前密码" error={errors.oldPassword?.message}>
          <PasswordInput
            id="current-password"
            autoComplete="current-password"
            {...form.register("oldPassword")}
          />
        </FormField>
        <FormField htmlFor="new-password" label="新密码" error={errors.password?.message}>
          <PasswordInput
            id="new-password"
            autoComplete="new-password"
            {...form.register("password")}
          />
        </FormField>
        <FormField
          htmlFor="confirm-password"
          label="确认新密码"
          error={errors.confirmation?.message}
        >
          <PasswordInput
            id="confirm-password"
            autoComplete="new-password"
            {...form.register("confirmation")}
          />
        </FormField>
        <div className="flex justify-end">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
            更新密码
          </Button>
        </div>
      </form>
    </ConsoleSection>
  );
}
