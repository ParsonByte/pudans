"use client";

import { ModalForm, ProFormSelect, ProFormText, ProFormTextArea } from "@ant-design/pro-components";
import type { User } from "@pudans-dev/api-client";
import { Divider, Flex, Tag, Typography } from "antd";
import { Mail, Smartphone } from "lucide-react";

import { USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

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
    const displayName = detail.spec.displayName || detail.metadata.name;
    const loginName = detail.spec.loginName || detail.metadata.name;
    const updateUser = useUpdateUser();

    const submit = async (values: UserEditorFormValues) => {
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
            prompt.success("用户已保存。");
            onSaved();
            return true;
        } catch {
            return false;
        }
    };

    return (
        <ModalForm<UserEditorFormValues>
            initialValues={{ bio: detail.spec.bio, loginName, userGroup: currentGroup }}
            layout="vertical"
            modalProps={{
                centered: true,
                destroyOnHidden: true,
                mask: { closable: !updateUser.isPending },
                styles: { body: { paddingTop: 10 } },
            }}
            onFinish={submit}
            onOpenChange={(open) => {
                if (!open && !updateUser.isPending) {
                    onClose();
                }
            }}
            open
            submitter={{
                searchConfig: { resetText: "取消", submitText: "保存更改" },
                resetButtonProps: { disabled: updateUser.isPending },
                submitButtonProps: { loading: updateUser.isPending },
            }}
            title="编辑用户"
            width={620}
        >
            <Flex align="flex-start" gap={16}>
                <UserAvatar name={displayName} size={56} src={detail.spec.avatar} />
                <div className="min-w-0 flex-1">
                    <Flex align="center" gap={8} wrap>
                        <Typography.Text className="truncate text-lg" strong>
                            {displayName}
                        </Typography.Text>
                        <Tag className="!mr-0" color="blue">
                            飞书同步
                        </Tag>
                    </Flex>
                    <Typography.Text className="mt-0.5 block font-mono text-xs" type="secondary">
                        @{loginName}
                    </Typography.Text>
                    <Flex className="mt-3" gap={20} wrap>
                        <Flex align="center" gap={6}>
                            <Mail className="text-neutral-400" size={14} />
                            <Typography.Text type={detail.spec.email ? undefined : "secondary"}>
                                {detail.spec.email || "未填写邮箱"}
                            </Typography.Text>
                        </Flex>
                        <Flex align="center" gap={6}>
                            <Smartphone className="text-neutral-400" size={14} />
                            <Typography.Text type={detail.spec.phone ? undefined : "secondary"}>
                                {detail.spec.phone || "未填写手机号"}
                            </Typography.Text>
                        </Flex>
                    </Flex>
                </div>
            </Flex>
            <Divider className="!my-6" />
            <div>
                <Typography.Text strong>本地账号设置</Typography.Text>
                <Typography.Text className="mt-1 block text-xs" type="secondary">
                    姓名、邮箱、手机号和头像由飞书同步；登录名、用户组和个人简介可在本地调整。
                </Typography.Text>
            </div>
            <div className="mt-4 grid gap-2">
                {canUpdate ? (
                    <ProFormText
                        extra="留空时恢复为系统默认登录名，资源标识不会改变。"
                        fieldProps={{
                            autoComplete: "username",
                            maxLength: 63,
                            placeholder: "输入新的登录名",
                        }}
                        label="登录名"
                        name="loginName"
                        rules={[
                            {
                                message: "登录名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾。",
                                pattern: USERNAME_PATTERN,
                            },
                        ]}
                    />
                ) : null}
                {canGroup ? (
                    <ProFormSelect
                        allowClear
                        fieldProps={{ optionFilterProp: "label", showSearch: true }}
                        label="用户组"
                        name="userGroup"
                        options={groupOptions}
                        placeholder="未分组"
                    />
                ) : null}
                {canUpdate ? (
                    <ProFormTextArea
                        fieldProps={{
                            autoSize: { maxRows: 6, minRows: 3 },
                            placeholder: "简单介绍这个账号的用途或职责",
                        }}
                        label="个人简介"
                        name="bio"
                    />
                ) : null}
            </div>
        </ModalForm>
    );
}
