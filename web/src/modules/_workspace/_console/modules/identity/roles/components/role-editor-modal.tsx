"use client";

import { ModalForm, ProFormText, ProFormTextArea } from "@ant-design/pro-components";
import type { Role } from "@pudans-dev/api-client";
import { Typography } from "antd";

import { USERNAME_PATTERN } from "@/constants/validation";

import { roleDescription, roleDisplayName } from "../../composables/_role";
import { useCreateRole, useUpdateRole } from "../composables/_role-actions";
import { buildRoleProfilePayload, createRolePayload } from "../composables/_role-permissions";

type RoleEditorValues = {
    description?: string;
    displayName: string;
    name: string;
};

type RoleEditorModalProps = {
    onClose: () => void;
    onSaved: (roleName: string) => void;
    role?: Role;
};

export function RoleEditorModal({ onClose, onSaved, role }: RoleEditorModalProps) {
    const createRole = useCreateRole();
    const updateRole = useUpdateRole();
    const pending = createRole.isPending || updateRole.isPending;

    const submit = async (values: RoleEditorValues) => {
        try {
            if (role) {
                await updateRole.mutateAsync({
                    name: role.metadata.name,
                    role: buildRoleProfilePayload(role, values),
                    successMessage: "角色信息已保存。",
                });
                onSaved(role.metadata.name);
            } else {
                const created = await createRole.mutateAsync(createRolePayload(values));
                onSaved(created.metadata.name);
            }
            return true;
        } catch {
            return false;
        }
    };

    return (
        <ModalForm<RoleEditorValues>
            initialValues={
                role
                    ? {
                          description: roleDescription(role),
                          displayName: roleDisplayName(role),
                          name: role.metadata.name,
                      }
                    : undefined
            }
            layout="vertical"
            modalProps={{
                centered: true,
                closable: !pending,
                destroyOnHidden: true,
                keyboard: !pending,
                mask: { closable: !pending },
                styles: { body: { paddingTop: 8 } },
            }}
            onFinish={submit}
            onOpenChange={(open) => {
                if (!open && !pending) {
                    onClose();
                }
            }}
            open
            submitTimeout={15_000}
            submitter={{
                searchConfig: { resetText: "取消", submitText: role ? "保存更改" : "创建角色" },
                resetButtonProps: { disabled: pending },
                submitButtonProps: { loading: pending },
            }}
            title={role ? "编辑角色" : "新建角色"}
            width={560}
        >
            <Typography.Paragraph className="!mb-5" type="secondary">
                {role
                    ? "角色标识不可修改。菜单、数据和操作权限可在角色详情中继续配置。"
                    : "创建后再为角色配置菜单、数据和操作权限。"}
            </Typography.Paragraph>
            {role ? (
                <ProFormText disabled fieldProps={{ className: "font-mono" }} label="角色标识" name="name" />
            ) : (
                <ProFormText
                    fieldProps={{ className: "font-mono", placeholder: "例如：security-auditor" }}
                    label="角色标识"
                    name="name"
                    rules={[
                        { message: "请输入角色标识。", required: true, whitespace: true },
                        {
                            message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
                            pattern: USERNAME_PATTERN,
                        },
                        { message: "角色标识不能超过 63 个字符。", max: 63 },
                    ]}
                />
            )}
            <ProFormText
                fieldProps={{ placeholder: "例如：安全审计员" }}
                label="显示名称"
                name="displayName"
                rules={[
                    { message: "请输入显示名称。", required: true, whitespace: true },
                    { message: "显示名称不能超过 64 个字符。", max: 64 },
                ]}
            />
            <ProFormTextArea
                fieldProps={{ maxLength: 240, placeholder: "可选，说明角色的职责与适用范围", rows: 3, showCount: true }}
                label="描述"
                name="description"
            />
        </ModalForm>
    );
}
