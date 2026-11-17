import { Controller, type Resolver, useFieldArray, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2, Users } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { PasswordInput } from "@/components/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { createLocalUsers, resolveUserApiError } from "../lib/api";
import { notifyBatchResult } from "../lib/notify-batch-result";
import { useUserGroups } from "../lib/use-user-groups";
import { passwordField, usernameField } from "../lib/validation";

const MAX_MANUAL_ROWS = 50;

const batchUserSchema = z.object({
  username: usernameField,
  password: passwordField,
  userGroup: z.string(),
});

const batchCreateSchema = z.object({
  users: z.array(batchUserSchema).min(1, "请至少添加一个用户。"),
});

type BatchCreateValues = z.infer<typeof batchCreateSchema>;

const EMPTY_ROW: BatchCreateValues["users"][number] = {
  username: "",
  password: "",
  userGroup: "",
};

type BatchCreateUsersDialogProps = {
  onClose: () => void;
};

export function BatchCreateUsersDialog({ onClose }: BatchCreateUsersDialogProps) {
  const queryClient = useQueryClient();
  const userGroupsQuery = useUserGroups();
  const form = useForm<BatchCreateValues>({
    resolver: zodResolver(batchCreateSchema) as Resolver<BatchCreateValues>,
    defaultValues: { users: [{ ...EMPTY_ROW }] },
  });
  const { fields, append, remove, replace } = useFieldArray({
    control: form.control,
    name: "users",
  });

  const mutation = useMutation({
    mutationFn: (values: BatchCreateValues) =>
      createLocalUsers(
        values.users.map((user) => ({
          username: user.username,
          password: user.password,
          userGroup: user.userGroup || undefined,
        })),
      ),
    onSuccess: async (result, values) => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      notifyBatchResult(result);
      if (result.failures.length === 0) {
        onClose();
        return;
      }
      const failedIndexes = new Set(result.failures.map((failure) => failure.index - 1));
      const failedRows = values.users.filter((_, index) => failedIndexes.has(index));
      replace(failedRows.length > 0 ? failedRows : [{ ...EMPTY_ROW }]);
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const groups = userGroupsQuery.data ?? [];

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Users />}
      title="批量创建用户"
      description="每行创建一个本地账号；姓名、邮箱、手机号和头像会在绑定飞书后同步。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-2xl"
      maxHeightRatio={0.6}
      submitLabel={`创建 ${fields.length} 个用户`}
      resetLabel="清空"
    >
      <div className="space-y-4">
        {fields.map((field, index) => {
          const errors = form.formState.errors.users?.[index];

          return (
            <div key={field.id} className="space-y-3 rounded-lg border p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground">用户 {index + 1}</span>
                {fields.length > 1 ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => remove(index)}
                    aria-label={`移除用户 ${index + 1}`}
                  >
                    <Trash2 />
                  </Button>
                ) : null}
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor={`batch-username-${field.id}`}>用户名</Label>
                  <Input
                    id={`batch-username-${field.id}`}
                    autoComplete="off"
                    spellCheck={false}
                    {...form.register(`users.${index}.username`)}
                  />
                  {errors?.username?.message ? (
                    <p className="text-xs text-destructive">{errors.username.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor={`batch-password-${field.id}`}>初始密码</Label>
                  <PasswordInput
                    id={`batch-password-${field.id}`}
                    autoComplete="new-password"
                    {...form.register(`users.${index}.password`)}
                  />
                  {errors?.password?.message ? (
                    <p className="text-xs text-destructive">{errors.password.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor={`batch-group-${field.id}`}>用户组</Label>
                  <Controller
                    control={form.control}
                    name={`users.${index}.userGroup`}
                    render={({ field: groupField }) => (
                      <Select
                        value={groupField.value || "default"}
                        onValueChange={(value) =>
                          groupField.onChange(value === "default" ? "" : value)
                        }
                        disabled={userGroupsQuery.isPending}
                      >
                        <SelectTrigger id={`batch-group-${field.id}`} className="w-full">
                          <SelectValue placeholder="选择用户组" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="default">默认用户组</SelectItem>
                          {groups.map((group) => (
                            <SelectItem key={group.metadata.name} value={group.metadata.name}>
                              {group.displayName || group.metadata.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
              </div>
            </div>
          );
        })}

        <Button
          type="button"
          variant="outline"
          onClick={() => append({ ...EMPTY_ROW })}
          disabled={fields.length >= MAX_MANUAL_ROWS}
        >
          <Plus />
          添加一行
        </Button>
        {fields.length >= MAX_MANUAL_ROWS ? (
          <p className="text-xs text-muted-foreground">
            单次最多创建 {MAX_MANUAL_ROWS} 个用户，更多请使用 CSV 导入。
          </p>
        ) : null}
      </div>
    </FormDialog>
  );
}
