"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, DatePicker, Form, Input, Select, Textarea } from "tdesign-react";
import type { FormRule } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { useSelfPermissionsQuery } from "../../../../../_uc/composables/_queries";
import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { useSudoConfirmation } from "../../../../components/sudo-confirm-dialog";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { SudoCancelledError, withSudoRetry } from "../../../../composables/_sudo";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    PAT_DESCRIPTION_MAX_LENGTH,
    PAT_NAME_MAX_LENGTH,
    PAT_QUERY_ROOT,
    type PatCreateValues,
    buildCreatePatPayload,
    defaultPatExpiryValue,
    patDisplayName,
    patToken,
} from "../composables/_pat";

type PatCreateModalProps = {
    onClose: () => void;
    username?: string;
};

type IssuedPat = {
    name: string;
    token: string;
};

function nameRules(): FormRule[] {
    return [
        { message: "请输入令牌名称。", required: true, whitespace: true },
        { message: `名称不能超过 ${PAT_NAME_MAX_LENGTH} 个字符。`, max: PAT_NAME_MAX_LENGTH },
    ];
}

function descriptionRules(): FormRule[] {
    return [{ message: `说明不能超过 ${PAT_DESCRIPTION_MAX_LENGTH} 个字符。`, max: PAT_DESCRIPTION_MAX_LENGTH }];
}

function expiryRules(): FormRule[] {
    return [
        { message: "请选择有效期。", required: true },
        {
            validator: (value) => {
                if (!value) {
                    return true;
                }

                const time = new Date(String(value).replace(" ", "T")).getTime();
                if (!Number.isFinite(time)) {
                    return { message: "有效期格式无效。", result: false };
                }

                return time > Date.now() ? true : { message: "有效期必须晚于当前时间。", result: false };
            },
        },
    ];
}

export function PatCreateModal({ onClose, username }: PatCreateModalProps) {
    const [form] = Form.useForm();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const { confirm, dialog: sudoDialog } = useSudoConfirmation();
    const permissionsQuery = useSelfPermissionsQuery();
    const [defaultExpiry] = useState(() => defaultPatExpiryValue());
    const [issued, setIssued] = useState<IssuedPat>();
    const roleOptions = (permissionsQuery.data?.roles ?? []).map((role) => ({ label: role, value: role }));
    const createToken = useMutation({
        mutationFn: async ({ owner, values }: { owner: string; values: PatCreateValues }) =>
            withSudoRetry(
                async () =>
                    (
                        await apiClient.personalAccessToken.generatePat({
                            personalAccessToken: buildCreatePatPayload(values, owner),
                        })
                    ).data,
                confirm,
            ),
        onError: (error) => {
            if (error instanceof SudoCancelledError) {
                return;
            }
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: PAT_QUERY_ROOT });
        },
    });

    const submit = async (values: PatCreateValues) => {
        if (!username) {
            prompt.error({ content: "无法确定当前用户，请刷新页面后重试。" });
            return;
        }

        try {
            const result = await createToken.mutateAsync({ owner: username, values });
            const token = patToken(result);
            if (!token) {
                prompt.warning({ content: "令牌已创建，但服务端未返回明文令牌，请在列表中查看。" });
                onClose();
                return;
            }

            setIssued({ name: patDisplayName(result), token });
        } catch {
            return;
        }
    };

    return (
        <>
            <GlobalDialog
                confirmLoading={createToken.isPending}
                footer={
                    issued ? (
                        <div className="flex justify-end">
                            <Button onClick={onClose} theme="primary">
                                完成
                            </Button>
                        </div>
                    ) : (
                        <IdentityDialogFooter
                            onCancel={onClose}
                            onSubmit={() => form.submit()}
                            pending={createToken.isPending}
                            submitLabel="创建令牌"
                        />
                    )
                }
                onClose={createToken.isPending ? () => undefined : onClose}
                open
                title={issued ? "令牌已创建" : "新建个人访问令牌"}
                width={520}
            >
                {issued ? (
                    <div className="flex min-w-0 flex-col gap-4">
                        <Alert
                            message="访问令牌仅显示一次，请立即复制并妥善保存；关闭后将无法再次查看。"
                            theme="warning"
                        />
                        <div className="flex min-w-0 flex-col gap-4 rounded-xl border border-[var(--td-component-border)] px-4 py-4">
                            <div className="flex min-w-0 flex-col gap-1">
                                <span className="text-xs text-[var(--td-text-color-placeholder)]">名称</span>
                                <CopyableIdentifier mask value={issued.name} />
                            </div>
                            <div className="flex min-w-0 flex-col gap-1">
                                <span className="text-xs text-[var(--td-text-color-placeholder)]">访问令牌</span>
                                <CopyableIdentifier mask value={issued.token} />
                            </div>
                        </div>
                    </div>
                ) : (
                    <Form
                        form={form}
                        initialData={{ description: "", expiresAt: defaultExpiry, name: "", roles: [] }}
                        labelAlign="top"
                        layout="vertical"
                        onSubmit={({ validateResult }) => {
                            if (validateResult !== true) {
                                return;
                            }

                            void submit(form.getFieldsValue(true) as PatCreateValues);
                        }}
                    >
                        <Form.FormItem
                            help="用于区分令牌用途，例如：CI 发布。"
                            label="名称"
                            name="name"
                            requiredMark
                            rules={nameRules()}
                        >
                            <Input placeholder="例如：ci-release" />
                        </Form.FormItem>
                        <Form.FormItem label="说明" name="description" rules={descriptionRules()}>
                            <Textarea
                                autosize={{ maxRows: 4, minRows: 2 }}
                                maxlength={PAT_DESCRIPTION_MAX_LENGTH}
                                placeholder="可选，说明令牌的使用场景。"
                            />
                        </Form.FormItem>
                        <Form.FormItem
                            help="到期后令牌自动失效。"
                            label="有效期至"
                            name="expiresAt"
                            requiredMark
                            rules={expiryRules()}
                        >
                            <DatePicker
                                enableTimePicker
                                format="YYYY-MM-DD HH:mm:ss"
                                placeholder="选择日期与时间"
                                style={{ width: "100%" }}
                                valueType="YYYY-MM-DD HH:mm:ss"
                            />
                        </Form.FormItem>
                        <Form.FormItem help="留空表示继承当前用户角色；角色创建后不可修改。" label="角色" name="roles">
                            <Select
                                clearable
                                filterable
                                loading={permissionsQuery.isPending}
                                multiple
                                options={roleOptions}
                                placeholder="默认继承当前用户角色"
                            />
                        </Form.FormItem>
                        <Alert
                            className="mt-2"
                            message="令牌以当前用户身份调用平台 API，请按用途单独签发并遵循最小权限。"
                            theme="info"
                        />
                    </Form>
                )}
            </GlobalDialog>
            {sudoDialog}
        </>
    );
}
