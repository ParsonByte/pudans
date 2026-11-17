import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { PasswordInput } from "@/components/password-input";
import { Label } from "@/components/ui/label";

import { resolveUserApiError } from "../lib/api";
import { passwordField } from "../lib/validation";

type ResetPasswordDialogProps = {
  name: string;
  onClose: () => void;
};

const resetPasswordSchema = z
  .object({
    password: passwordField,
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "两次输入的密码不一致。",
  });

type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export function ResetPasswordDialog({ name, onClose }: ResetPasswordDialogProps) {
  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema) as Resolver<ResetPasswordValues>,
    defaultValues: { password: "", confirmPassword: "" },
  });

  const mutation = useMutation({
    mutationFn: async (values: ResetPasswordValues) => {
      await apiClient.user.changeUserPassword({
        name,
        passwordRequest: { password: values.password },
      });
    },
    onSuccess: () => {
      toast.success("密码已重置。");
      onClose();
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<KeyRound />}
      title="重置密码"
      description={`为「${name}」设置新的登录密码。`}
      form={form}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.45}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="reset-password">新密码</Label>
          <PasswordInput
            id="reset-password"
            autoComplete="new-password"
            {...form.register("password")}
          />
          {form.formState.errors.password?.message ? (
            <p className="text-xs text-destructive">{form.formState.errors.password.message}</p>
          ) : (
            <p className="text-xs text-muted-foreground">8-257 位，仅限字母、数字和 !@#$%^&*.?</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="reset-confirm-password">确认密码</Label>
          <PasswordInput
            id="reset-confirm-password"
            autoComplete="new-password"
            {...form.register("confirmPassword")}
          />
          {form.formState.errors.confirmPassword?.message ? (
            <p className="text-xs text-destructive">
              {form.formState.errors.confirmPassword.message}
            </p>
          ) : null}
        </div>
      </div>
    </FormDialog>
  );
}
