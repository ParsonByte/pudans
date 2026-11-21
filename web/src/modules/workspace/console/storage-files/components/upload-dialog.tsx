import { useEffect, useMemo, useState } from "react";
import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { StoragePolicyItem } from "@/modules/workspace/console/storage-policies/lib/api";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import { resolveStorageFileApiError, uploadStorageFile } from "../lib/api";

type StorageUploadValues = {
  policy: string;
  owner: string;
  module: string;
  file?: FileList;
};

type StorageUploadDialogProps = {
  policies: StoragePolicyItem[];
  onClose: () => void;
  onUploaded: () => void;
};

const uploadSchema = z.object({
  policy: z.string().trim(),
  owner: z.string().trim().max(64, "所有者标识不能超过 64 个字符。"),
  module: z.string().trim().max(64, "模块标识不能超过 64 个字符。"),
  file: z.custom<FileList | undefined>(
    (value) => value instanceof FileList && value.length > 0,
    "请选择要上传的文件。",
  ),
});

export function StorageUploadDialog({ policies, onClose, onUploaded }: StorageUploadDialogProps) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState(0);
  const availablePolicies = useMemo(
    () => policies.filter((policy) => policy.status?.ready !== false && policy.metadata?.name),
    [policies],
  );
  const defaultPolicyName = useMemo(() => {
    const preferred = availablePolicies.find((policy) => policy.spec.defaultPolicy);
    return (preferred ?? availablePolicies[0])?.metadata.name;
  }, [availablePolicies]);
  const form = useForm<StorageUploadValues>({
    resolver: zodResolver(uploadSchema) as Resolver<StorageUploadValues>,
    defaultValues: { policy: "", owner: "", module: "", file: undefined },
  });
  const policyName = useWatch({ control: form.control, name: "policy" });
  const fileList = useWatch({ control: form.control, name: "file" });
  const file = fileList?.[0] ?? null;
  const selectedPolicy = availablePolicies.find((policy) => policy.metadata.name === policyName);
  const currentUserQuery = useQuery({
    queryKey: ["account-profile"],
    queryFn: async () => (await apiClient.user.getCurrentUser()).data,
    retry: false,
  });
  const currentUid = currentUserQuery.data?.spec.uid;
  const maxSize = selectedPolicy?.spec.maxSize ?? 0;
  const fileError = !file
    ? null
    : file.size <= 0
      ? "文件内容为空。"
      : maxSize > 0 && file.size > maxSize
        ? `文件大小超过策略上限 ${formatFileSize(maxSize)}。`
        : null;

  useEffect(() => {
    if (defaultPolicyName && !form.getValues("policy")) {
      form.setValue("policy", defaultPolicyName);
    }
  }, [defaultPolicyName, form]);

  useEffect(() => {
    if (currentUid && !form.getValues("owner")) {
      form.setValue("owner", currentUid);
    }
  }, [currentUid, form]);

  const mutation = useMutation({
    mutationFn: async (values: StorageUploadValues) => {
      const selected = values.file?.[0];
      if (!selected) {
        throw new Error("请选择要上传的文件。");
      }
      setProgress(0);
      return uploadStorageFile(
        selected,
        {
          policy: values.policy.trim() || undefined,
          owner: values.owner.trim() || undefined,
          module: values.module.trim() || undefined,
        },
        setProgress,
      );
    },
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ["storage-files"] });
      toast.success(`文件「${created.spec.originalName || created.metadata.name}」已上传。`);
      onUploaded();
      onClose();
    },
    onError: (error) => {
      setProgress(0);
      toast.error(
        error instanceof Error && !("response" in error)
          ? error.message
          : resolveStorageFileApiError(error),
      );
    },
  });

  const submit = form.handleSubmit(
    (values) => {
      if (fileError) {
        toast.error(fileError);
        return;
      }
      mutation.mutate(values);
    },
    () => toast.error("请检查填写内容。"),
  );

  const close = () => {
    if (!mutation.isPending) {
      onClose();
    }
  };

  return (
    <FormDialog
      open
      onClose={close}
      icon={<Upload />}
      title="上传存储文件"
      description="文件会通过分片上传写入所选存储策略，完成后出现在文件列表。"
      form={form}
      onSubmit={submit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.6}
      submitLabel="上传文件"
      resetLabel="清空"
    >
      <FormField
        label="存储策略"
        htmlFor="storage-file-upload-policy"
        error={form.formState.errors.policy?.message}
        hint={
          availablePolicies.length > 0
            ? "只列出已就绪的存储策略。"
            : "未获取到已就绪的存储策略，上传将使用默认策略。"
        }
      >
        <Controller
          control={form.control}
          name="policy"
          render={({ field }) => (
            <Select
              value={field.value}
              onValueChange={field.onChange}
              disabled={mutation.isPending || availablePolicies.length === 0}
            >
              <SelectTrigger id="storage-file-upload-policy" aria-label="选择存储策略">
                <SelectValue placeholder="使用默认策略" />
              </SelectTrigger>
              <SelectContent>
                {availablePolicies.map((policy) => (
                  <SelectItem key={policy.metadata.name} value={policy.metadata.name}>
                    {policy.spec.displayName || policy.metadata.name}
                    {policy.spec.defaultPolicy ? "（默认）" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </FormField>

      <FormField
        label="文件"
        htmlFor="storage-file-upload-file"
        error={form.formState.errors.file?.message}
        hint="上传期间请保持页面打开。"
      >
        <Input
          id="storage-file-upload-file"
          type="file"
          disabled={mutation.isPending}
          aria-label="选择要上传的文件"
          {...form.register("file")}
        />
        {file ? (
          <p className="truncate text-xs text-muted-foreground">
            {file.name} · {formatFileSize(file.size)}
          </p>
        ) : null}
        {fileError ? <p className="text-xs text-destructive">{fileError}</p> : null}
      </FormField>

      <FormField
        label="所属模块"
        htmlFor="storage-file-upload-module"
        error={form.formState.errors.module?.message}
        hint="可选，用于按模块筛选文件。"
      >
        <Input
          id="storage-file-upload-module"
          autoComplete="off"
          spellCheck={false}
          placeholder="例如 console"
          {...form.register("module")}
        />
      </FormField>

      <FormField
        label="所有者"
        htmlFor="storage-file-upload-owner"
        error={form.formState.errors.owner?.message}
        hint="默认为当前用户 ID。"
      >
        <Input
          id="storage-file-upload-owner"
          autoComplete="off"
          spellCheck={false}
          {...form.register("owner")}
        />
      </FormField>

      {mutation.isPending ? (
        <div className="space-y-2">
          <Progress value={progress} />
          <p className="text-xs text-muted-foreground">
            {progress >= 100 ? "上传完成，正在确认文件…" : `正在上传 ${progress}%`}
          </p>
        </div>
      ) : null}
    </FormDialog>
  );
}
