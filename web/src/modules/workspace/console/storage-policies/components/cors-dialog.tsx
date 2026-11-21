import { useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { Loader2, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { configureStoragePolicyCors, resolveStorageApiError } from "../lib/api";

type CorsDialogProps = {
  name: string;
  onClose: () => void;
  onSaved: () => void;
};

export function CorsDialog({ name, onClose, onSaved }: CorsDialogProps) {
  const [origins, setOrigins] = useState("*");
  const mutation = useMutation({
    mutationFn: (values: string[]) => configureStoragePolicyCors(name, values),
    onSuccess: onSaved,
    onError: (error) => toast.error(resolveStorageApiError(error)),
  });

  const submit = () => {
    const values = origins
      .split(/[,，\s]+/)
      .map((value) => value.trim())
      .filter(Boolean);
    if (values.length === 0) {
      toast.error("请输入至少一个来源。");
      return;
    }
    mutation.mutate(values);
  };

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="配置跨域策略"
      description="允许浏览器直接访问该存储桶。多个来源用逗号分隔，* 表示允许所有来源。"
      icon={<ShieldCheck className="size-5" />}
      size="lg"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
            取消
          </Button>
          <Button type="button" onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
            提交配置
          </Button>
        </>
      }
    >
      <FormField label="允许的来源" htmlFor="storage-cors-origins">
        <Input
          id="storage-cors-origins"
          value={origins}
          onChange={(event) => setOrigins(event.target.value)}
          placeholder="https://example.com, https://app.example.com"
        />
      </FormField>
    </DialogFormShell>
  );
}
