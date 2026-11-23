"use client";

import type { User } from "@pudans-dev/api-client";
import { MailIcon, MobileIcon } from "tdesign-icons-react";
import { Divider, Form, Input, Select, Textarea, Typography } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";
import { USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useUpdateUser } from "../composables/_user-actions";
import type { UserGroupOption } from "../composables/_user-groups";
import { UserAvatar } from "./user-avatar";

type UserEditorFormValues = {
    bio?: string;
    loginName?: string;
    userGroup?: string;
};

type UserEditorModalProps = {
    canGroup: boolean;
    canUpdate: boolean;
    currentGroup?: string;
    detail: User;
    groupOptions: UserGroupOption[];
    onClose: () => void;
    onSaved: () => void;
};

export function UserEditorModal({
    canGroup,
    canUpdate,
    currentGroup,
    detail,
    groupOptions,
    onClose,
    onSaved,
}: UserEditorModalProps) {
    const prompt = useMessagePrompt();
    const tokens = useDesignTokens();
    const [form] = Form.useForm();
    const displayName = detail.spec.displayName || detail.metadata.name;
    const loginName = detail.spec.loginName || detail.metadata.name;
    const updateUser = useUpdateUser();

    const submit = async () => {
        const values = form.getFieldsValue(true) as UserEditorFormValues;

        try {
            await updateUser.mutateAsync({
                bio: values.bio,
                canGroup,
                canUpdate,
                currentGroup,
                loginName: values.loginName,
                name: detail.metadata.name,
                userGroup: values.userGroup,
            });
            prompt.success({ content: "用户已保存。" });
            onSaved();
        } catch {
            return;
        }
    };
    const close = () => {
        if (!updateUser.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={updateUser.isPending}
                    submitLabel="保存更改"
                />
            }
            onClose={close}
            open
            title="编辑用户"
            width={620}
        >
            <div className="flex items-start gap-4">
                <UserAvatar name={displayName} size={56} src={detail.spec.avatar} />
                <div className="min-w-0 flex-1">
                    <Typography.Text className="truncate text-lg" strong>
                        {displayName}
                    </Typography.Text>
                    <Typography.Text className="mt-0.5 block font-mono text-xs" theme="secondary">
                        @{loginName}
                    </Typography.Text>
                    <div className="mt-3 flex flex-wrap gap-5">
                        <span className="flex items-center gap-1.5">
                            <MailIcon style={{ color: tokens.colorTextTertiary }} />
                            <Typography.Text theme={detail.spec.email ? undefined : "secondary"}>
                                {detail.spec.email || "未填写邮箱"}
                            </Typography.Text>
                        </span>
                        <span className="flex items-center gap-1.5">
                            <MobileIcon style={{ color: tokens.colorTextTertiary }} />
                            <Typography.Text theme={detail.spec.phone ? undefined : "secondary"}>
                                {detail.spec.phone || "未填写手机号"}
                            </Typography.Text>
                        </span>
                    </div>
                </div>
            </div>
            <Divider className="!my-6" />
            <Form
                form={form}
                initialData={{ bio: detail.spec.bio, loginName, userGroup: currentGroup }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <div className="grid gap-4 sm:grid-cols-2">
                    {canUpdate ? (
                        <Form.FormItem
                            label="登录名"
                            name="loginName"
                            rules={[
                                {
                                    message: "登录名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾。",
                                    pattern: USERNAME_PATTERN,
                                },
                            ]}
                        >
                            <Input autocomplete="username" maxlength={63} />
                        </Form.FormItem>
                    ) : null}
                    {canGroup ? (
                        <Form.FormItem label="用户组" name="userGroup">
                            <Select clearable filterable options={groupOptions} placeholder="未分组" />
                        </Form.FormItem>
                    ) : null}
                </div>
                {canUpdate ? (
                    <Form.FormItem label="个人简介" name="bio">
                        <Textarea autosize={{ maxRows: 6, minRows: 3 }} />
                    </Form.FormItem>
                ) : null}
            </Form>
        </GlobalDialog>
    );
}
