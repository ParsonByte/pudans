import { useState } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Mail } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { resolveParameterSettingError } from "../lib/parameter-api";

const mailTestSchema = z.object({
  recipient: z.email("请输入有效的收件邮箱。"),
});

type MailTestValues = z.infer<typeof mailTestSchema>;

export function MailTestDialog() {
  const [open, setOpen] = useState(false);
  const form = useForm<MailTestValues>({
    resolver: zodResolver(mailTestSchema) as Resolver<MailTestValues>,
    defaultValues: { recipient: "" },
  });

  const mutation = useMutation({
    mutationFn: (to: string) =>
      apiClient.parameterSetting.sendParameterSettingTestMail({
        sendParameterSettingTestMailRequest: { to },
      }),
    onSuccess: () => {
      toast.success("测试邮件已发送，请查收。");
      form.reset();
      setOpen(false);
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values.recipient.trim()),
    (errors) => toast.error(errors.recipient?.message ?? "请检查填写内容。"),
  );

  const handleClose = () => {
    setOpen(false);
    mutation.reset();
  };

  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
        <Mail />
        发送测试邮件
      </Button>
      <FormDialog
        open={open}
        onClose={handleClose}
        icon={<Mail />}
        title="发送测试邮件"
        description="使用已保存的 SMTP 配置向指定邮箱发送一封测试邮件，可用于验证发信配置是否正确。"
        form={form}
        onSubmit={onSubmit}
        isPending={mutation.isPending}
        contentClassName="sm:max-w-md"
        maxHeightRatio={0.4}
        submitLabel="发送"
        resetLabel="清空"
      >
        <FormField
          label="收件邮箱"
          htmlFor="mail-test-recipient"
          error={form.formState.errors.recipient?.message}
        >
          <Input
            id="mail-test-recipient"
            type="email"
            autoComplete="email"
            placeholder="输入接收测试邮件的邮箱"
            disabled={mutation.isPending}
            {...form.register("recipient")}
          />
        </FormField>
      </FormDialog>
    </>
  );
}
