"use client";

import type { UserGroup } from "@pudans-dev/api-client";
import { Form, Input, Textarea } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { USERNAME_PATTERN } from "@/constants/validation";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import {
    type UserGroupProfileValues,
    buildUserGroupProfilePayload,
    createUserGroupPayload,
    userGroupDescription,
    userGroupDisplayName,
} from "../composables/_user-group";
import { useCreateUserGroup, useUpdateUserGroup } from "../composables/_user-group-actions";

type UserGroupEditorModalProps = {
    group?: UserGroup;
    onClose: () => void;
    onSaved: (groupName: string) => void;
};

export function UserGroupEditorModal({ group, onClose, onSaved }: UserGroupEditorModalProps) {
    const [form] = Form.useForm();
    const createGroup = useCreateUserGroup();
    const updateGroup = useUpdateUserGroup();
    const pending = createGroup.isPending || updateGroup.isPending;

    const submit = async () => {
        const values = form.getFieldsValue(true) as UserGroupProfileValues;

        try {
            if (group) {
                await updateGroup.mutateAsync({
                    name: group.metadata.name,
                    successMessage: "用户组信息已保存。",
                    userGroup: buildUserGroupProfilePayload(group, values),
                });
                onSaved(group.metadata.name);
            } else {
                const created = await createGroup.mutateAsync(createUserGroupPayload(values));
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
                    submitLabel={group ? "保存更改" : "创建用户组"}
                />
            }
            onClose={close}
            open
            title={group ? "编辑用户组" : "新建用户组"}
            width={560}
        >
            <Form
                form={form}
                initialData={
                    group
                        ? {
                              description: userGroupDescription(group),
                              displayName: userGroupDisplayName(group),
                              name: group.metadata.name,
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
                    label="用户组标识"
                    name="name"
                    rules={
                        group
                            ? undefined
                            : [
                                  { message: "请输入用户组标识。", required: true, whitespace: true },
                                  {
                                      message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
                                      pattern: USERNAME_PATTERN,
                                  },
                                  { message: "用户组标识不能超过 63 个字符。", max: 63 },
                              ]
                    }
                >
                    <Input disabled={Boolean(group)} />
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
