"use client";

import { Form, Input, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useCreateFeishuUser } from "../composables/_feishu-create";
import { FEISHU_USER_ID_PATTERN, type FeishuUserDraft, normalizeFeishuUserDraft } from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";

type CreateUserModalProps = {
    groupOptions: UserGroupOption[];
    onClose: () => void;
};

export function CreateUserModal({ groupOptions, onClose }: CreateUserModalProps) {
    const prompt = useMessagePrompt();
    const createUser = useCreateFeishuUser();
    const [form] = Form.useForm();

    const submit = async () => {
        const values = form.getFieldsValue(true) as FeishuUserDraft;

        try {
            await createUser.mutateAsync(normalizeFeishuUserDraft(values));
            prompt.success({ content: "用户已添加。" });
            onClose();
        } catch {
            return;
        }
    };
    const close = () => {
        if (!createUser.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={createUser.isPending}
                    submitLabel="添加用户"
                />
            }
            onClose={close}
            open
            title="新建用户"
            width={520}
        >
            <Form
                form={form}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <Form.FormItem
                    label="飞书用户 ID"
                    name="providerUserId"
                    rules={[
                        { message: "请输入飞书用户 ID。", required: true, whitespace: true },
                        {
                            message: "飞书用户 ID 需要以 on_ 或 ou_ 开头。",
                            pattern: FEISHU_USER_ID_PATTERN,
                        },
                    ]}
                >
                    <Input clearable={false} placeholder="on_xxxxxxxx 或 ou_xxxxxxxx" />
                </Form.FormItem>
                <Form.FormItem
                    label="登录名"
                    name="username"
                    rules={[
                        { message: "登录名不能超过 63 个字符。", max: 63 },
                        {
                            message: "登录名只能包含小写字母、数字、- 和 .。",
                            pattern: USERNAME_PATTERN,
                        },
                    ]}
                >
                    <Input clearable={false} placeholder="留空时自动生成" />
                </Form.FormItem>
                <Form.FormItem label="用户组" name="userGroup">
                    <Select clearable={false} options={groupOptions} placeholder="使用默认用户组" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
