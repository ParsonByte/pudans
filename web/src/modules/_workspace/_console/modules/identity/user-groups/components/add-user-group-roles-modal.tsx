"use client";

import { useMemo } from "react";

import type { Role, UserGroup } from "@pudans-dev/api-client";
import { Form, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { ROLE_TEMPLATE_LABEL, isBuiltinRole, roleDisplayName } from "../../composables/_role";
import { buildUserGroupRolePayload, userGroupRoleNames } from "../composables/_user-group";
import { useUpdateUserGroup } from "../composables/_user-group-actions";

type AddUserGroupRolesValues = {
    roles: string[];
};

type AddUserGroupRolesModalProps = {
    group: UserGroup;
    onClose: () => void;
    onSaved: () => void;
    roles: Role[];
};

export function AddUserGroupRolesModal({ group, onClose, onSaved, roles }: AddUserGroupRolesModalProps) {
    const [form] = Form.useForm();
    const updateGroup = useUpdateUserGroup();
    const boundRoleNames = useMemo(() => new Set(userGroupRoleNames(group)), [group]);
    const options = useMemo(
        () =>
            roles
                .filter(
                    (role) =>
                        !isBuiltinRole(role) &&
                        role.metadata.labels?.[ROLE_TEMPLATE_LABEL] !== "true" &&
                        !boundRoleNames.has(role.metadata.name),
                )
                .map((role) => ({
                    label: roleDisplayName(role),
                    value: role.metadata.name,
                })),
        [boundRoleNames, roles],
    );

    const submit = async () => {
        const values = form.getFieldsValue(true) as AddUserGroupRolesValues;
        const selected = values.roles ?? [];
        if (selected.length === 0) {
            return;
        }

        try {
            await updateGroup.mutateAsync({
                affectsPermissions: true,
                name: group.metadata.name,
                successMessage: "角色绑定已更新。",
                userGroup: buildUserGroupRolePayload(group, Array.from(new Set([...boundRoleNames, ...selected]))),
            });
            onSaved();
        } catch {
            return;
        }
    };
    const close = () => {
        if (!updateGroup.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={updateGroup.isPending}
                    submitLabel="添加角色"
                />
            }
            onClose={close}
            open
            title="绑定角色"
            width={560}
        >
            <Form
                form={form}
                initialData={{ roles: [] }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <Form.FormItem
                    label="角色"
                    name="roles"
                    rules={[
                        {
                            validator: (value) =>
                                Array.isArray(value) && value.length > 0
                                    ? true
                                    : { message: "请选择至少一个角色。", result: false },
                        },
                    ]}
                >
                    <Select
                        clearable={false}
                        filterable
                        multiple
                        options={options}
                        placeholder={options.length > 0 ? "选择要绑定的角色" : "没有可绑定的自定义角色"}
                    />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
