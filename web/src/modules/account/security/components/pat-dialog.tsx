"use client";

import { useEffect } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import type { PersonalAccessToken } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormField } from "@/components/form-field";
import { useSudo } from "@/components/sudo/sudo-provider";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { resolveApiErrorWithDetail } from "@/lib/api-error";
import { beijingDateTimeInputToIso, beijingDateTimeInputValue } from "@/lib/date";

import {
  type CreatedPat,
  PAT_LIST_QUERY_KEY,
  createPat,
  fetchPatDetail,
  patDetailQueryKey,
  updatePat,
} from "../lib/pats";

const DEFAULT_PAT_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;

const ERROR_MESSAGES = {
  notFound: "个人访问令牌不存在或已删除。",
  conflict: "操作失败，请稍后重试。",
};

const patSchema = z.object({
  name: z.string().trim().min(1, "请输入令牌名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "说明不能超过 500 个字符。"),
  expiresAt: z
    .string()
    .trim()
    .min(1, "请选择过期时间。")
    .refine((value) => Boolean(beijingDateTimeInputToIso(value)), "过期时间无效。"),
});

type PatFormValues = z.infer<typeof patSchema>;

type PatDialogProps = {
  open: boolean;
  patName: string | null;
  onOpenChange: (open: boolean) => void;
  onCreated: (created: CreatedPat) => void;
};

function formDefaults(pat?: PersonalAccessToken): PatFormValues {
  return {
    name: pat?.spec?.name ?? "",
    description: pat?.spec?.description ?? "",
    expiresAt: beijingDateTimeInputValue(
      pat?.spec?.expiresAt ?? new Date(Date.now() + DEFAULT_PAT_LIFETIME_MS),
    ),
  };
}

export function PatDialog({ open, patName, onOpenChange, onCreated }: PatDialogProps) {
  const { requireSudo } = useSudo();
  const queryClient = useQueryClient();
  const editing = patName !== null;
  const detailQuery = useQuery({
    queryKey: patDetailQueryKey(patName ?? ""),
    queryFn: () => fetchPatDetail(patName ?? ""),
    enabled: open && editing,
    retry: false,
  });
  const form = useForm<PatFormValues>({
    resolver: zodResolver(patSchema) as Resolver<PatFormValues>,
    defaultValues: formDefaults(),
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    form.reset(formDefaults(editing ? detailQuery.data : undefined));
  }, [detailQuery.data, editing, form, open]);

  const mutation = useMutation({
    mutationFn: async (values: PatFormValues): Promise<CreatedPat> => {
      const expiresAt = beijingDateTimeInputToIso(values.expiresAt);
      if (!expiresAt) {
        throw new Error("过期时间无效。");
      }
      const description = values.description.trim() || undefined;
      if (editing) {
        if (!detailQuery.data) {
          throw new Error("个人访问令牌详情尚未加载。");
        }
        const pat = await updatePat(detailQuery.data, {
          name: values.name.trim(),
          description,
          expiresAt,
        });
        return { pat };
      }
      await requireSudo();
      return createPat({ name: values.name.trim(), description, expiresAt });
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: PAT_LIST_QUERY_KEY });
      if (editing && patName) {
        queryClient.setQueryData(patDetailQueryKey(patName), result.pat);
        toast.success("个人访问令牌已更新。");
      } else {
        toast.success("个人访问令牌已创建，请立即保存明文。");
        onCreated(result);
      }
      onOpenChange(false);
    },
    onError: (error) => {
      toast.error(
        error instanceof Error && !("response" in error)
          ? error.message
          : resolveApiErrorWithDetail(error, ERROR_MESSAGES),
      );
    },
  });

  const submit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );
  const errors = form.formState.errors;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!mutation.isPending) {
          onOpenChange(next);
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "编辑个人访问令牌" : "生成个人访问令牌"}</DialogTitle>
          <DialogDescription>
            {editing
              ? "更新令牌的名称、说明与过期时间；令牌密钥本身不会改变。"
              : "令牌明文仅在创建成功后显示一次，请立即复制并妥善保存。"}
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={submit} noValidate>
          {editing ? (
            <FormField label="令牌标识" htmlFor="pat-metadata-name" hint="创建后不可修改。">
              <Input id="pat-metadata-name" value={patName ?? ""} className="font-mono" disabled />
            </FormField>
          ) : null}
          <FormField label="名称" htmlFor="pat-name" error={errors.name?.message}>
            <Input
              id="pat-name"
              autoComplete="off"
              spellCheck={false}
              placeholder="例如：CLI 脚本"
              {...form.register("name")}
            />
          </FormField>
          <FormField label="说明" htmlFor="pat-description" error={errors.description?.message}>
            <Textarea
              id="pat-description"
              rows={2}
              placeholder="用途、使用位置或负责人"
              {...form.register("description")}
            />
          </FormField>
          <FormField
            label="过期时间"
            htmlFor="pat-expires-at"
            hint="按北京时间填写；到期后令牌立即失效。"
            error={errors.expiresAt?.message}
          >
            <Input id="pat-expires-at" type="datetime-local" {...form.register("expiresAt")} />
          </FormField>
          {editing && detailQuery.isError ? (
            <p className="text-xs text-destructive">无法读取令牌详情，请关闭后重试。</p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={mutation.isPending}
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={mutation.isPending || (editing && !detailQuery.data)}>
              {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
              {editing ? "保存修改" : "生成令牌"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
