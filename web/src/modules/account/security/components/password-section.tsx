import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

const ERROR_MESSAGES = {
  notFound: "用户不存在。",
  conflict: "密码修改失败，请稍后重试。",
};

export function PasswordSection() {
  const [oldPassword, setOldPassword] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");

  const mutation = useMutation({
    mutationFn: async () =>
      (await apiClient.user.changeMyPassword({ passwordRequest: { oldPassword, password } })).data,
    onSuccess: () => {
      toast.success("密码已更新。");
      setOldPassword("");
      setPassword("");
      setConfirmation("");
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const submit = () => {
    if (!oldPassword || !password || !confirmation) {
      toast.error("请填写完整的密码信息。");
      return;
    }
    if (password.length < 8) {
      toast.error("新密码至少需要 8 位。");
      return;
    }
    if (password !== confirmation) {
      toast.error("两次输入的新密码不一致。");
      return;
    }
    mutation.mutate();
  };

  return (
    <ConsoleSection>
      <ConsoleSectionHeader title="修改密码" description="修改后需要重新使用新密码登录。" />
      <div className="grid max-w-xl gap-4 p-5">
        <div className="space-y-2">
          <Label htmlFor="current-password">当前密码</Label>
          <PasswordInput
            id="current-password"
            autoComplete="current-password"
            value={oldPassword}
            onChange={(event) => setOldPassword(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="new-password">新密码</Label>
          <PasswordInput
            id="new-password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm-password">确认新密码</Label>
          <PasswordInput
            id="confirm-password"
            autoComplete="new-password"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </div>
        <div className="flex justify-end">
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
            更新密码
          </Button>
        </div>
      </div>
    </ConsoleSection>
  );
}
