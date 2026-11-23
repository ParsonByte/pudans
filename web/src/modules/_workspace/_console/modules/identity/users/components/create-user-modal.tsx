"use client";

import { ModalForm, ProFormSelect, ProFormText } from "@ant-design/pro-components";

import { USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { useCreateFeishuUser } from "../composables/_feishu-create";
import { FEISHU_USER_ID_PATTERN, type FeishuUserDraft, normalizeFeishuUserDraft } from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";
import { FeishuSyncNotice } from "./feishu-sync-notice";

type CreateUserModalProps = {
    groupOptions: UserGroupOption[];
    onClose: () => void;
};

export function CreateUserModal({ groupOptions, onClose }: CreateUserModalProps) {
    const prompt = useMessagePrompt();
    const createUser = useCreateFeishuUser();

    const submit = async (values: FeishuUserDraft) => {
        try {
            await createUser.mutateAsync(normalizeFeishuUserDraft(values));
            prompt.success("用户已添加。");
            onClose();
            return true;
        } catch {
            return false;
        }
    };

    return (
        <ModalForm<FeishuUserDraft>
            modalProps={{ destroyOnHidden: true, mask: { closable: !createUser.isPending } }}
            onFinish={submit}
            onOpenChange={(open) => {
                if (!open && !createUser.isPending) {
                    onClose();
                }
            }}
            open
            submitter={{
                searchConfig: { submitText: "添加用户" },
                submitButtonProps: { loading: createUser.isPending },
            }}
            title="新建用户"
            width={520}
        >
            <FeishuSyncNotice />
            <ProFormText
                className="mt-5"
                fieldProps={{ placeholder: "on_xxxxxxxx 或 ou_xxxxxxxx" }}
                label="飞书用户 ID"
                name="providerUserId"
                rules={[
                    { message: "请输入飞书用户 ID。", required: true, whitespace: true },
                    {
                        message: "飞书用户 ID 需要以 on_ 或 ou_ 开头。",
                        pattern: FEISHU_USER_ID_PATTERN,
                    },
                ]}
            />
            <ProFormText
                extra="留空时由系统自动生成。"
                fieldProps={{ placeholder: "可选，例如：alice" }}
                label="登录名"
                name="username"
                rules={[
                    { message: "登录名不能超过 63 个字符。", max: 63 },
                    {
                        message: "登录名只能包含小写字母、数字、- 和 .。",
                        pattern: USERNAME_PATTERN,
                    },
                ]}
            />
            <ProFormSelect
                allowClear
                label="用户组"
                name="userGroup"
                options={groupOptions}
                placeholder="使用默认用户组"
            />
        </ModalForm>
    );
}
