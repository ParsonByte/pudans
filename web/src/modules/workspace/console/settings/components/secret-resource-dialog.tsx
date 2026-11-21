"use client";

import { useId, useMemo, useState } from "react";
import { type FieldErrors, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus, Save, Trash2, Undo2 } from "lucide-react";
import { toast } from "sonner";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormDialogSection } from "@/components/form-dialog";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type { ExtensionRecord } from "../lib/extension-records";
import {
  buildExtensionResourceRecord,
  createExtensionResource,
  extensionResourceFields,
  resolveExtensionResourceError,
  updateExtensionResource,
} from "../lib/extension-resources";
import {
  type ParameterFormValues,
  createParameterFormValues,
  createParameterRequestBody,
  createParameterSchema,
} from "../lib/parameter-fields";
import { ParameterFieldControl } from "./parameter-field";

type SecretResourceDialogProps = {
  record?: ExtensionRecord;
  onClose: () => void;
};

type SecretRow = {
  key: string;
  value: string;
  existing: boolean;
};

export function SecretResourceDialog({ record, onClose }: SecretResourceDialogProps) {
  const formId = useId();
  const queryClient = useQueryClient();
  const fields = extensionResourceFields("secrets", Boolean(record));
  const form = useForm<ParameterFormValues>({
    resolver: zodResolver(createParameterSchema(fields)) as Resolver<ParameterFormValues>,
    defaultValues: createParameterFormValues(
      fields,
      (record ?? {}) as unknown as Record<string, unknown>,
    ),
  });
  const initialKeys = useMemo(() => existingSecretKeys(record), [record]);
  const initialRows = useMemo<SecretRow[]>(
    () => initialKeys.map((key) => ({ key, value: "", existing: true })),
    [initialKeys],
  );
  const [rows, setRows] = useState<SecretRow[]>(initialRows);
  const [rowError, setRowError] = useState("");

  const mutation = useMutation({
    mutationFn: (next: ExtensionRecord) =>
      record
        ? updateExtensionResource("secrets", record.metadata.name, next)
        : createExtensionResource("secrets", next),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["extension-resources"] });
      toast.success(record ? "密钥已保存。" : "密钥已创建。");
      onClose();
    },
    onError: (error) => {
      toast.error(resolveExtensionResourceError(error));
    },
  });

  const commitRows = (next: SecretRow[]) => {
    setRows(next);
    setRowError("");
  };

  const updateRow = (index: number, patch: Partial<SecretRow>) => {
    commitRows(
      rows.map((row, currentIndex) => (currentIndex === index ? { ...row, ...patch } : row)),
    );
  };

  const onSubmit = form.handleSubmit(
    (values) => {
      const message = buildSecretPayload(record, rows);
      if (typeof message === "string") {
        setRowError(message);
        return;
      }
      setRowError("");
      const body = createParameterRequestBody(fields, values);
      const next = buildExtensionResourceRecord("secrets", record, body);
      next.data = message.data;
      next.stringData = message.stringData;
      mutation.mutate(next);
    },
    (errors: FieldErrors<ParameterFormValues>) => {
      const firstMessage = Object.values(errors).find(
        (error) => typeof error?.message === "string",
      )?.message;

      toast.error(typeof firstMessage === "string" ? firstMessage : "请检查填写内容。");
    },
  );

  return (
    <DialogFormShell
      open
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
      title={record ? "编辑密钥" : "新建密钥"}
      description="密钥项的值不会回显；输入新值即可替换，留空表示保持原值。"
      footerMode="always"
      contentClassName="sm:max-w-2xl"
      footer={
        <>
          <Button
            type="button"
            variant="outline"
            disabled={mutation.isPending}
            onClick={() => {
              form.reset();
              commitRows(initialRows);
            }}
          >
            <Undo2 />
            撤销更改
          </Button>
          <Button type="submit" form={formId} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
            保存更改
          </Button>
        </>
      }
    >
      <form id={formId} className="space-y-6" onSubmit={onSubmit} noValidate>
        <FormDialogSection title="基本信息">
          <div className="space-y-5">
            {fields.fields.map((field) => (
              <ParameterFieldControl
                key={field.name}
                form={form}
                groupKey="extension-resource"
                field={field}
                userGroupOptions={[]}
                userGroupsPending={false}
              />
            ))}
          </div>
        </FormDialogSection>

        <FormDialogSection title="密钥项">
          <div className="space-y-2">
            {rows.map((row, index) => (
              <div key={index} className="flex items-start gap-2">
                <Input
                  value={row.key}
                  readOnly={row.existing}
                  placeholder="键"
                  className="font-mono"
                  aria-label={`密钥键 ${index + 1}`}
                  onChange={(event) => updateRow(index, { key: event.target.value })}
                />
                <PasswordInput
                  value={row.value}
                  placeholder={row.existing ? "已配置，输入新值以替换" : "值"}
                  className="font-mono"
                  aria-label={`密钥值 ${index + 1}`}
                  onChange={(event) => updateRow(index, { value: event.target.value })}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label="删除该密钥项"
                  onClick={() => commitRows(rows.filter((_, item) => item !== index))}
                >
                  <Trash2 />
                </Button>
              </div>
            ))}
            {rows.length === 0 ? (
              <p className="text-xs leading-5 text-muted-foreground">暂未添加密钥项。</p>
            ) : null}
            <p className="text-xs leading-5 text-muted-foreground">
              已保存的值以“已配置”展示，不会出现在列表或表单中。
            </p>
            {rowError ? <p className="text-xs text-destructive">{rowError}</p> : null}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => commitRows([...rows, { key: "", value: "", existing: false }])}
            >
              <Plus />
              添加密钥项
            </Button>
          </div>
        </FormDialogSection>
      </form>
    </DialogFormShell>
  );
}

function existingSecretKeys(record: ExtensionRecord | undefined): string[] {
  return [
    ...new Set([...Object.keys(record?.data ?? {}), ...Object.keys(record?.stringData ?? {})]),
  ];
}

function buildSecretPayload(
  record: ExtensionRecord | undefined,
  rows: readonly SecretRow[],
): { data: Record<string, string>; stringData: Record<string, string> } | string {
  const existingKeys = new Set(existingSecretKeys(record));
  const data: Record<string, string> = { ...(record?.data ?? {}) };
  const stringData: Record<string, string> = { ...(record?.stringData ?? {}) };
  const keptKeys = new Set<string>();
  const seenKeys = new Set<string>();

  for (const row of rows) {
    const key = row.key.trim();

    if (!key) {
      if (row.value !== "") {
        return "请为密钥项填写键名。";
      }
      continue;
    }
    if (seenKeys.has(key)) {
      return `密钥键「${key}」重复。`;
    }
    seenKeys.add(key);
    keptKeys.add(key);

    if (row.value !== "") {
      stringData[key] = row.value;
      delete data[key];
    } else if (!existingKeys.has(key)) {
      return `请为密钥键「${key}」填写值。`;
    }
  }

  for (const key of existingKeys) {
    if (!keptKeys.has(key)) {
      delete data[key];
      delete stringData[key];
    }
  }

  return { data, stringData };
}
