"use client";

import { useState } from "react";

import { ModalForm, ProFormSelect, ProFormTextArea } from "@ant-design/pro-components";
import type { BatchCreateResponse } from "@pudans-dev/api-client";
import { Form } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { useBatchCreateFeishuUsers } from "../composables/_feishu-create";
import {
    MAX_USER_BATCH_SIZE,
    parseFeishuUserIds,
    selectFailedFeishuUserDrafts,
    validateFeishuUserDrafts,
} from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";
import { BatchCreateResult } from "./batch-create-result";
import { FeishuSyncNotice } from "./feishu-sync-notice";

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
    const [form] = Form.useForm<BatchCreateFormValues>();
    const [result, setResult] = useState<BatchCreateResponse>();
    const createUsers = useBatchCreateFeishuUsers();

    const validateUserIds = async (_: unknown, value?: string) => {
        const parsed = parseFeishuUserIds(value ?? "");

        if (parsed.rows.length === 0) {
            throw new Error("请输入至少一个飞书用户 ID。");
        }
        if (parsed.issues.length > 0) {
            throw new Error(parsed.issues[0].message);
        }
    };

    const submit = async (values: BatchCreateFormValues) => {
        const users = parseFeishuUserIds(values.userIds).rows.map((user) => ({
            ...user,
            userGroup: values.userGroup,
        }));
        const issues = validateFeishuUserDrafts(users);

        if (issues.length > 0) {
            prompt.error(issues[0].message);
            return false;
        }

        setResult(undefined);

        try {
            const response = await createUsers.mutateAsync(users);
            const created = response.created ?? 0;
            const failed = response.failed ?? 0;

            setResult(response);

            if (failed === 0) {
                prompt.success(`已添加 ${created} 个用户。`);
                onClose();
                return true;
            }

            const retryUsers = selectFailedFeishuUserDrafts(users, response.failures);
            prompt.warning(`已添加 ${created} 个用户，${failed} 个未能添加，失败项已保留。`);
            form.setFieldsValue({
                userIds: retryUsers.map((user) => user.providerUserId).join("\n"),
            });
            return false;
        } catch {
            return false;
        }
    };

    return (
        <ModalForm<BatchCreateFormValues>
            form={form}
            modalProps={{ destroyOnHidden: true, mask: { closable: !createUsers.isPending } }}
            onFinish={submit}
            onOpenChange={(open) => {
                if (!open && !createUsers.isPending) {
                    onClose();
                }
            }}
            open
            submitter={{
                searchConfig: { submitText: "批量添加" },
                submitButtonProps: { loading: createUsers.isPending },
            }}
            title="批量添加用户"
            width={560}
        >
            <FeishuSyncNotice />
            <ProFormTextArea
                className="mt-5"
                extra={`每行一个；单次最多 ${MAX_USER_BATCH_SIZE} 个，本地登录名会自动生成。`}
                fieldProps={{
                    autoSize: { maxRows: 8, minRows: 5 },
                    placeholder: "on_xxxxxxxx\nou_xxxxxxxx",
                }}
                label="飞书用户 ID"
                name="userIds"
                rules={[{ validator: validateUserIds }]}
                tooltip="支持 union_id 或 open_id。"
            />
            <ProFormSelect
                allowClear
                className="mt-4"
                label="用户组"
                name="userGroup"
                options={groupOptions}
                placeholder="使用默认用户组"
            />
            {result ? (
                <div className="mt-4">
                    <BatchCreateResult {...result} />
                </div>
            ) : null}
        </ModalForm>
    );
}
