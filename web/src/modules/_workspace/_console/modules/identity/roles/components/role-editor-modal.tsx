"use client";

import type { Role } from "@pudans-dev/api-client";
import { Form, Input, Textarea } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { USERNAME_PATTERN } from "@/constants/validation";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
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
    const [form] = Form.useForm();
    const createRole = useCreateRole();
    const updateRole = useUpdateRole();
    const pending = createRole.isPending || updateRole.isPending;

    const submit = async () => {
        const values = form.getFieldsValue(true) as RoleEditorValues;

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
        } catch {
            return;
        }
    };
    const close = () => {
        if (!pending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel={role ? "保存更改" : "创建角色"}
                />
            }
            onClose={close}
            open
            title={role ? "编辑角色" : "新建角色"}
            width={560}
        >
            <Form
                form={form}
                initialData={
                    role
                        ? {
                              description: roleDescription(role),
                              displayName: roleDisplayName(role),
                              name: role.metadata.name,
                          }
                        : undefined
                }
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <Form.FormItem
                    label="角色标识"
                    name="name"
                    rules={
                        role
                            ? undefined
                            : [
                                  { message: "请输入角色标识。", required: true, whitespace: true },
                                  {
                                      message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
                                      pattern: USERNAME_PATTERN,
                                  },
                                  { message: "角色标识不能超过 63 个字符。", max: 63 },
                              ]
                    }
                >
                    <Input disabled={Boolean(role)} />
                </Form.FormItem>
                <Form.FormItem
                    label="显示名称"
                    name="displayName"
                    rules={[
                        { message: "请输入显示名称。", required: true, whitespace: true },
                        { message: "显示名称不能超过 64 个字符。", max: 64 },
                    ]}
                >
                    <Input />
                </Form.FormItem>
                <Form.FormItem label="描述" name="description">
                    <Textarea count maxlength={240} />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
