import { useMemo } from "react";
import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldPlus } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { beijingDateTimeInputToIso, beijingDateTimeInputValue } from "@/lib/date";
import { useSessionContext } from "@/modules/auth/lib/session-context";
import { RoleRefSelector } from "@/modules/workspace/console/user-groups/components/role-ref-selector";
import { usernameField } from "@/modules/workspace/console/users/lib/validation";

import {
  type TempRoleBindingRecord,
  applyTempRoleBinding,
  resolveTempRoleBindingApiError,
  updateTempRoleBinding,
} from "../lib/api";
import { tempRoleBindingRoleNames, tempRoleOptionList } from "../lib/binding-model";
import { useRoles } from "../lib/use-roles";

type TempRoleBindingDialogProps = {
  binding?: TempRoleBindingRecord;
  onClose: () => void;
};

const MAX_DURATION_MILLIS = 8 * 60 * 60 * 1000;

const tempRoleBindingSchema = z
  .object({
    username: usernameField,
    roleNames: z.array(z.string()).min(1, "请至少选择一个角色。"),
    expiresAt: z.string().min(1, "请选择到期时间。"),
    reason: z.string().trim().max(500, "申请原因不能超过 500 个字符。"),
    ticketId: z.string().trim().max(100, "工单号不能超过 100 个字符。"),
  })
  .superRefine((values, ctx) => {
    const expiresAt = beijingDateTimeInputToIso(values.expiresAt);
    if (!expiresAt) {
      ctx.addIssue({ code: "custom", path: ["expiresAt"], message: "请选择有效的到期时间。" });
      return;
    }
    const expiresAtMillis = new Date(expiresAt).getTime();
    if (expiresAtMillis <= Date.now()) {
      ctx.addIssue({ code: "custom", path: ["expiresAt"], message: "到期时间必须晚于当前时间。" });
    } else if (expiresAtMillis > Date.now() + MAX_DURATION_MILLIS) {
      ctx.addIssue({
        code: "custom",
        path: ["expiresAt"],
        message: "临时授权最长不能超过 8 小时。",
      });
    }
  });

type TempRoleBindingValues = z.infer<typeof tempRoleBindingSchema>;

function defaultExpiresAt(): string {
  return beijingDateTimeInputValue(new Date(Date.now() + 60 * 60 * 1000));
}

export function TempRoleBindingDialog({ binding, onClose }: TempRoleBindingDialogProps) {
  const queryClient = useQueryClient();
  const { session } = useSessionContext();
  const rolesQuery = useRoles();
  const roleOptions = useMemo(
    () => tempRoleOptionList(rolesQuery.data ?? []).map((option) => ({ ...option, locked: false })),
    [rolesQuery.data],
  );
  const form = useForm<TempRoleBindingValues>({
    resolver: zodResolver(tempRoleBindingSchema) as Resolver<TempRoleBindingValues>,
    defaultValues: {
      username: binding?.spec.username || session.username || "",
      roleNames: tempRoleBindingRoleNames(binding?.spec.roleNames),
      expiresAt: binding ? beijingDateTimeInputValue(binding.spec.expiresAt) : defaultExpiresAt(),
      reason: binding?.spec.reason ?? "",
      ticketId: binding?.spec.ticketId ?? "",
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: TempRoleBindingValues) => {
      const expiresAt = beijingDateTimeInputToIso(values.expiresAt);
      if (!expiresAt) {
        throw new Error("无效的到期时间。");
      }
      const input = {
        username: values.username,
        roleNames: values.roleNames,
        expiresAt,
        reason: values.reason || undefined,
        ticketId: values.ticketId || undefined,
      };
      if (binding) {
        await updateTempRoleBinding(binding, input);
      } else {
        await applyTempRoleBinding(input);
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["temp-role-bindings"] }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      toast.success(binding ? "临时角色绑定已更新。" : "临时授权申请已提交，等待审批。");
      onClose();
    },
    onError: (error) => toast.error(resolveTempRoleBindingApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const roleNamesError = form.formState.errors.roleNames?.message;

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ShieldPlus />}
      title={binding ? "编辑临时授权" : "申请临时授权"}
      description="临时授权需要审批后生效；到期后自动收回，最长 8 小时。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-xl"
      maxHeightRatio={0.65}
      submitLabel={binding ? "保存更改" : "提交申请"}
      resetLabel="清空"
    >
      <FormField
        label="用户"
        htmlFor="temp-role-username"
        error={form.formState.errors.username?.message}
        hint="默认为当前用户；只有超级管理员可以为其他用户申请。"
      >
        <Input
          id="temp-role-username"
          autoComplete="off"
          spellCheck={false}
          {...form.register("username")}
        />
      </FormField>

      <FormField
        label="到期时间"
        htmlFor="temp-role-expires-at"
        error={form.formState.errors.expiresAt?.message}
        hint="使用北京时间，必须晚于当前时间且不超过 8 小时。"
      >
        <Input id="temp-role-expires-at" type="datetime-local" {...form.register("expiresAt")} />
      </FormField>

      <div className="space-y-2">
        <p className="text-sm font-medium">角色</p>
        <p className="text-xs text-muted-foreground">
          选择临时授予的角色；super-role 不能被临时授予。
        </p>
        <Controller
          control={form.control}
          name="roleNames"
          render={({ field }) => (
            <RoleRefSelector
              options={roleOptions}
              selected={field.value}
              isLoading={rolesQuery.isPending}
              isError={rolesQuery.isError}
              onChange={field.onChange}
            />
          )}
        />
        {roleNamesError ? <p className="text-xs text-destructive">{roleNamesError}</p> : null}
      </div>

      <FormField
        label="申请原因"
        htmlFor="temp-role-reason"
        error={form.formState.errors.reason?.message}
      >
        <Textarea
          id="temp-role-reason"
          rows={3}
          placeholder="说明临时授权的业务原因。"
          {...form.register("reason")}
        />
      </FormField>

      <FormField
        label="工单号"
        htmlFor="temp-role-ticket"
        error={form.formState.errors.ticketId?.message}
        hint="可关联变更或审批工单。"
      >
        <Input
          id="temp-role-ticket"
          autoComplete="off"
          spellCheck={false}
          {...form.register("ticketId")}
        />
      </FormField>
    </FormDialog>
  );
}
