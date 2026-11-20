import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { Copy } from "lucide-react";
import { z } from "zod";

import { Input } from "@/components/ui/input";

import { RESOURCE_NAME_HINT, RESOURCE_NAME_PATTERN } from "../lib/model";
import { ProviderDialogShell, ProviderFieldRow } from "./provider-dialog-shell";

const copySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入资源 ID。")
    .max(63, "资源 ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
});

export type ResourceCopyValues = z.infer<typeof copySchema>;

type ResourceCopyDialogProps = {
  title: string;
  description: string;
  defaultName: string;
  defaultDisplayName: string;
  existingNames: ReadonlySet<string>;
  isPending: boolean;
  submitLabel?: string;
  onSubmit: (values: ResourceCopyValues) => void;
  onClose: () => void;
};

export function ResourceCopyDialog({
  title,
  description,
  defaultName,
  defaultDisplayName,
  existingNames,
  isPending,
  submitLabel = "复制",
  onSubmit,
  onClose,
}: ResourceCopyDialogProps) {
  const form = useForm<ResourceCopyValues>({
    resolver: zodResolver(copySchema) as Resolver<ResourceCopyValues>,
    defaultValues: { name: defaultName, displayName: defaultDisplayName },
  });

  const submit = form.handleSubmit((values) => {
    if (existingNames.has(values.name)) {
      form.setError("name", { message: "资源 ID 已存在，请更换。" });
      return;
    }
    onSubmit(values);
  });

  return (
    <ProviderDialogShell
      title={title}
      description={description}
      icon={<Copy className="size-4" />}
      onSubmit={submit}
      onClose={onClose}
      isPending={isPending}
      submitLabel={submitLabel}
    >
      <ProviderFieldRow
        label="资源 ID"
        hint={RESOURCE_NAME_HINT}
        error={form.formState.errors.name?.message}
      >
        <Input aria-invalid={Boolean(form.formState.errors.name)} {...form.register("name")} />
      </ProviderFieldRow>
      <ProviderFieldRow label="名称" error={form.formState.errors.displayName?.message}>
        <Input
          aria-invalid={Boolean(form.formState.errors.displayName)}
          {...form.register("displayName")}
        />
      </ProviderFieldRow>
    </ProviderDialogShell>
  );
}
