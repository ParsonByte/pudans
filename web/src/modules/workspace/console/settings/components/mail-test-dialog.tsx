import { type FormEvent, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Loader2, Mail, Send } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import { resolveParameterSettingError } from "../lib/parameter-api";

export function MailTestDialog() {
  const [open, setOpen] = useState(false);
  const [recipient, setRecipient] = useState("");

  const mutation = useMutation({
    mutationFn: (to: string) =>
      apiClient.parameterSetting.sendParameterSettingTestMail({
        sendParameterSettingTestMailRequest: { to },
      }),
    onSuccess: () => {
      toast.success("测试邮件已发送，请查收。");
      setRecipient("");
      setOpen(false);
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = recipient.trim();

    if (!z.email().safeParse(value).success) {
      toast.error("请输入有效的收件邮箱。");
      return;
    }

    mutation.mutate(value);
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);

        if (!nextOpen) {
          mutation.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button type="button" variant="outline" size="sm">
          <Mail />
          发送测试邮件
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <form className="space-y-4" onSubmit={handleSubmit} noValidate>
          <DialogHeader>
            <DialogTitle>发送测试邮件</DialogTitle>
            <DialogDescription>
              使用已保存的 SMTP 配置向指定邮箱发送一封测试邮件，可用于验证发信配置是否正确。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor="mail-test-recipient">收件邮箱</Label>
            <Input
              id="mail-test-recipient"
              type="email"
              autoComplete="email"
              placeholder="输入接收测试邮件的邮箱"
              value={recipient}
              onChange={(event) => setRecipient(event.target.value)}
              disabled={mutation.isPending}
            />
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={mutation.isPending}
              onClick={() => setOpen(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="animate-spin" /> : <Send />}
              发送
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
