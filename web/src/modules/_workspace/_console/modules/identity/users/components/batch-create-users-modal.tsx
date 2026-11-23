"use client";

import { useState } from "react";

import type { BatchCreateResponse } from "@pudans-dev/api-client";
import { Form, Select, Textarea } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useBatchCreateFeishuUsers } from "../composables/_feishu-create";
import {
    MAX_USER_BATCH_SIZE,
    parseFeishuUserIds,
    selectFailedFeishuUserDrafts,
    validateFeishuUserDrafts,
} from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";
import { BatchCreateResult } from "./batch-create-result";

type BatchCreateFormValues = {
    userIds: string;
    userGroup?: string;
};

type BatchCreateUsersModalProps = {
    groupOptions: UserGroupOption[];
    onClose: () => void;
};

export function BatchCreateUsersModal({ groupOptions, onClose }: BatchCreateUsersModalProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [result, setResult] = useState<BatchCreateResponse>();
    const createUsers = useBatchCreateFeishuUsers();

    const validateUserIds = (value: unknown) => {
        const parsed = parseFeishuUserIds(typeof value === "string" ? value : "");

        if (parsed.rows.length === 0) {
            return { message: "请输入至少一个飞书用户 ID。", result: false };
        }
        if (parsed.issues.length > 0) {
            return { message: parsed.issues[0].message, result: false };
        }

        return true;
    };

    const submit = async () => {
        const values = form.getFieldsValue(true) as BatchCreateFormValues;
        const parsed = parseFeishuUserIds(values.userIds ?? "");

        const users = parsed.rows.map((user) => ({
            ...user,
            userGroup: values.userGroup,
        }));
        const issues = validateFeishuUserDrafts(users);

        if (issues.length > 0) {
            prompt.error({ content: issues[0].message });
            return;
        }

        setResult(undefined);

        try {
            const response = await createUsers.mutateAsync(users);
            const created = response.created ?? 0;
            const failed = response.failed ?? 0;

            setResult(response);

            if (failed === 0) {
                prompt.success({ content: `已添加 ${created} 个用户。` });
                onClose();
                return;
            }

            const retryUsers = selectFailedFeishuUserDrafts(users, response.failures);
            prompt.warning({ content: `已添加 ${created} 个用户，${failed} 个未能添加，失败项已保留。` });
            form.setFieldsValue({
                userIds: retryUsers.map((user) => user.providerUserId).join("\n"),
            });
        } catch {
            return;
        }
    };
    const close = () => {
        if (!createUsers.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={createUsers.isPending}
                    submitLabel="批量添加"
                />
            }
            onClose={close}
            open
            title="批量添加用户"
            width={560}
        >
            <Form
                form={form}
                initialData={{ userIds: "" }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <Form.FormItem
                    label={`飞书用户 ID（单次最多 ${MAX_USER_BATCH_SIZE} 个）`}
                    name="userIds"
                    rules={[{ validator: validateUserIds }]}
                >
                    <Textarea
                        autosize={{ maxRows: 8, minRows: 5 }}
                        placeholder="on_xxxxxxxx&#10;ou_xxxxxxxx"
                    />
                </Form.FormItem>
                <Form.FormItem label="用户组" name="userGroup">
                    <Select clearable={false} options={groupOptions} placeholder="使用默认用户组" />
                </Form.FormItem>
            </Form>
            {result ? (
                <div className="mt-4">
                    <BatchCreateResult created={result.created} failed={result.failed} failures={result.failures} />
                </div>
            ) : null}
        </GlobalDialog>
    );
}
