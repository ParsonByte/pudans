import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserCog } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

import { resolveUserApiError } from "../lib/api";

type EditUserDialogProps = {
  name: string;
  bio?: string;
  onClose: () => void;
};

const editUserSchema = z.object({
  bio: z.string().trim().max(500, "个人简介不能超过 500 个字符。"),
});

type EditUserValues = z.infer<typeof editUserSchema>;

export function EditUserDialog({ name, bio, onClose }: EditUserDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<EditUserValues>({
    resolver: zodResolver(editUserSchema) as Resolver<EditUserValues>,
    defaultValues: {
      bio: bio ?? "",
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: EditUserValues) => {
      await apiClient.user.updateUser({
        name,
        updateProfileRequest: {
          bio: values.bio.trim(),
        },
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["user", name] });
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("个人简介已更新。");
      onClose();
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<UserCog />}
      title="编辑简介"
      description="姓名、邮箱、手机号和头像由飞书同步，不能在这里修改。"
      form={form}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.4}
    >
      <div className="space-y-2">
        <Label htmlFor="edit-bio">个人简介</Label>
        <Textarea id="edit-bio" rows={4} {...form.register("bio")} />
        {form.formState.errors.bio?.message ? (
          <p className="text-xs text-destructive">{form.formState.errors.bio.message}</p>
        ) : null}
      </div>
    </FormDialog>
  );
}
