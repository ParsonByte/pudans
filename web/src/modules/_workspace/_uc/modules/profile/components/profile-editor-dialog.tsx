"use client";

import type { UserView } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Form, Textarea, Typography } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { UC_PROFILE_QUERY_KEY } from "../../../composables/_queries";

type ProfileEditorFormValues = {
    bio?: string;
};

type ProfileEditorDialogProps = {
    onClose: () => void;
    user: UserView;
};

export function ProfileEditorDialog({ onClose, user }: ProfileEditorDialogProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const updateProfile = useMutation({
        mutationFn: async (values: ProfileEditorFormValues) =>
            apiClient.user.updateCurrentUser({
                updateProfileRequest: { bio: values.bio?.trim() ?? "" },
            }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: UC_PROFILE_QUERY_KEY });
            prompt.success({ content: "个人简介已更新。" });
            onClose();
        },
    });
    const displayName = user.spec?.displayName || user.metadata?.name || "当前用户";
    const loginName = user.spec?.loginName || user.metadata?.name;

    return (
        <GlobalDialog
            confirmLoading={updateProfile.isPending}
            confirmText="保存更改"
            onClose={() => {
                if (!updateProfile.isPending) {
                    onClose();
                }
            }}
            onConfirm={() => form.submit()}
            open
            title="编辑个人资料"
            width={560}
        >
            <div className="mb-5 flex flex-col gap-1">
                <Typography.Text strong>{displayName}</Typography.Text>
                <Typography.Text className="font-mono text-xs" theme="secondary">
                    @{loginName}
                </Typography.Text>
                <Typography.Text className="text-xs" theme="secondary">
                    显示名称、邮箱、手机和头像由飞书同步，不能在此修改。
                </Typography.Text>
            </div>
            <Form
                form={form}
                initialData={{ bio: user.spec?.bio }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void updateProfile.mutateAsync(form.getFieldsValue(true) as ProfileEditorFormValues);
                    }
                }}
            >
                <Form.FormItem label="个人简介" name="bio">
                    <Textarea
                        autosize={{ maxRows: 8, minRows: 4 }}
                        maxlength={500}
                        placeholder="写一句关于自己的介绍"
                    />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
