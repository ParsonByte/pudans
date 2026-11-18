import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Users } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { resolveUserApiError } from "../lib/api";

type ChangeGroupDialogProps = {
  name: string;
  currentGroup?: string;
  onClose: () => void;
};

const changeGroupSchema = z.object({
  userGroup: z.string().min(1, "请选择用户组。"),
});

type ChangeGroupValues = z.infer<typeof changeGroupSchema>;

export function ChangeGroupDialog({ name, currentGroup, onClose }: ChangeGroupDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<ChangeGroupValues>({
    resolver: zodResolver(changeGroupSchema) as Resolver<ChangeGroupValues>,
    defaultValues: { userGroup: currentGroup ?? "" },
  });

  const userGroupsQuery = useUserGroups();

  const groups = userGroupsQuery.data ?? [];
  const options = groups.map((group) => ({
    value: group.metadata.name,
    label: group.displayName || group.metadata.name,
  }));
  if (currentGroup && !options.some((option) => option.value === currentGroup)) {
    options.unshift({ value: currentGroup, label: currentGroup });
  }

  const mutation = useMutation({
    mutationFn: async (values: ChangeGroupValues) => {
      await apiClient.user.assignUserGroup({
        name,
        assignGroupRequest: { userGroup: values.userGroup },
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["user-group", name] });
      await queryClient.invalidateQueries({ queryKey: ["user-permissions", name] });
      await queryClient.invalidateQueries({ queryKey: ["user", name] });
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("用户组已更新。");
      onClose();
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Users />}
      title="调整用户组"
      description="用户组决定用户继承的角色；一个用户只能属于一个用户组。"
      form={form}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.35}
    >
      <div className="space-y-2">
        <Label htmlFor="change-group">用户组</Label>
        <Controller
          control={form.control}
          name="userGroup"
          render={({ field }) => (
            <Select
              value={field.value}
              onValueChange={field.onChange}
              disabled={userGroupsQuery.isPending}
            >
              <SelectTrigger id="change-group" className="w-full">
                <SelectValue placeholder="选择用户组" />
              </SelectTrigger>
              <SelectContent>
                {options.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
        {form.formState.errors.userGroup?.message ? (
          <p className="text-xs text-destructive">{form.formState.errors.userGroup.message}</p>
        ) : null}
        {options.length === 0 && !userGroupsQuery.isPending ? (
          <p className="text-xs text-muted-foreground">暂无可用的用户组。</p>
        ) : null}
      </div>
    </FormDialog>
  );
}
