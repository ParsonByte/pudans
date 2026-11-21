import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FileUp } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import {
  AI_PROVIDER_FILE_PURPOSES,
  type AiProviderFilePurpose,
  resolveAiProviderApiError,
  uploadProviderFile,
} from "../lib/api";
import { PROVIDER_FILE_PURPOSE_OPTIONS } from "../lib/model";

const purposeValues = [...AI_PROVIDER_FILE_PURPOSES] as [
  AiProviderFilePurpose,
  ...AiProviderFilePurpose[],
];

const uploadSchema = z.object({
  storageFileId: z.string().trim().min(1, "请输入存储文件 ID。").max(200, "存储文件 ID 过长。"),
  purpose: z.enum(purposeValues),
  expiresAfterSeconds: z
    .string()
    .trim()
    .refine(
      (value) =>
        value === "" || (/^\d+$/.test(value) && Number(value) >= 3600 && Number(value) <= 2592000),
      "有效期必须在 3600 到 2592000 秒之间。",
    ),
});

type UploadFormValues = z.infer<typeof uploadSchema>;

type ProviderFileUploadDialogProps = {
  providerName: string;
  onClose: () => void;
};

export function ProviderFileUploadDialog({ providerName, onClose }: ProviderFileUploadDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<UploadFormValues>({
    resolver: zodResolver(uploadSchema) as Resolver<UploadFormValues>,
    defaultValues: { storageFileId: "", purpose: "user_data", expiresAfterSeconds: "" },
  });

  const mutation = useMutation({
    mutationFn: (values: UploadFormValues) =>
      uploadProviderFile(providerName, {
        storageFileId: values.storageFileId.trim(),
        purpose: values.purpose,
        expiresAfterSeconds:
          values.expiresAfterSeconds === "" ? undefined : Number(values.expiresAfterSeconds),
      }),
    onSuccess: async (file) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-files", providerName] });
      toast.success(`文件「${file.filename || file.id}」已上传到供应商文件库。`);
      onClose();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<FileUp className="size-5" />}
      title="上传供应商文件"
      description="把平台存储中的文件登记到供应商文件库，文件内容由后端读取。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-xl"
      submitLabel="上传文件"
    >
      <FormDialogSection title="文件来源">
        <FormField
          label="存储文件 ID"
          htmlFor="provider-file-storage-id"
          hint="可在「存储文件」页面查看处于正常状态的文件 ID。"
          error={form.formState.errors.storageFileId?.message}
        >
          <Input
            id="provider-file-storage-id"
            className="font-mono"
            placeholder="storage-file-..."
            {...form.register("storageFileId")}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="用途"
            htmlFor="provider-file-purpose"
            error={form.formState.errors.purpose?.message}
          >
            <Controller
              control={form.control}
              name="purpose"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="provider-file-purpose" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PROVIDER_FILE_PURPOSE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            label="有效期（秒）"
            htmlFor="provider-file-expiry"
            hint="留空使用供应商默认有效期。"
            error={form.formState.errors.expiresAfterSeconds?.message}
          >
            <Input
              id="provider-file-expiry"
              type="number"
              min={3600}
              max={2592000}
              placeholder="3600"
              {...form.register("expiresAfterSeconds")}
            />
          </FormField>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}
