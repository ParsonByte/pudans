"use client";

import { useEffect, useMemo, useState } from "react";

import type { ListedUser } from "@pudans-dev/api-client";
import { Form, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useAddUserGroupMembers } from "../composables/_user-group-actions";
import { useUserGroupUserSearchQuery } from "../composables/_user-group-queries";

type AddGroupMembersValues = {
    users: string[];
};

type AddUserGroupMembersModalProps = {
    groupName: string;
    memberNames: Set<string>;
    onClose: () => void;
};

export function AddUserGroupMembersModal({ groupName, memberNames, onClose }: AddUserGroupMembersModalProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [keyword, setKeyword] = useState("");
    const [selectedNames, setSelectedNames] = useState<string[]>([]);
    const [selectedLabels, setSelectedLabels] = useState<Map<string, string>>(new Map());
    const searchQuery = useUserGroupUserSearchQuery(keyword);
    const addMembers = useAddUserGroupMembers();

    useEffect(() => {
        if (searchQuery.error) {
            prompt.error({ content: consoleErrorMessage(searchQuery.error) });
        }
    }, [prompt, searchQuery.error]);

    const options = useMemo(() => {
        const searchOptions = (searchQuery.data ?? [])
            .filter((user) => !memberNames.has(user.name))
            .map((user) => ({
                label: userOptionLabel(user),
                value: user.name,
            }));
        const retainedOptions = selectedNames
            .filter((name) => !searchOptions.some((option) => option.value === name))
            .map((name) => ({
                label: selectedLabels.get(name) ?? name,
                value: name,
            }));

        return [...retainedOptions, ...searchOptions];
    }, [memberNames, searchQuery.data, selectedLabels, selectedNames]);

    const rememberSelection = (values: Partial<AddGroupMembersValues>) => {
        const names = Array.isArray(values.users) ? values.users : [];
        setSelectedNames(names);
        setSelectedLabels((previous) => {
            const next = new Map(previous);
            for (const user of searchQuery.data ?? []) {
                if (names.includes(user.name)) {
                    next.set(user.name, userOptionLabel(user));
                }
            }
            for (const name of next.keys()) {
                if (!names.includes(name)) {
                    next.delete(name);
                }
            }
            return next;
        });
    };

    const submit = async () => {
        const values = form.getFieldsValue(true) as AddGroupMembersValues;
        const usernames = Array.isArray(values.users) ? values.users : [];
        if (usernames.length === 0) {
            return;
        }

        try {
            const result = await addMembers.mutateAsync({ groupName, usernames });
            if (result.failures.length === 0) {
                prompt.success({ content: `已添加 ${result.added.length} 个成员。` });
                onClose();
                return;
            }

            const failedNames = result.failures.map((failure) => failure.name);
            form.setFieldsValue({ users: failedNames });
            setSelectedNames(failedNames);
            prompt.warning({
                content: `已添加 ${result.added.length} 个成员，${result.failures.length} 个失败，失败项已保留。`,
            });
        } catch {
            return;
        }
    };
    const close = () => {
        if (!addMembers.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={addMembers.isPending}
                    submitLabel="添加成员"
                />
            }
            onClose={close}
            open
            title="添加组员"
            width={620}
        >
            <Form
                form={form}
                initialData={{ users: [] }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
                onValuesChange={(values) => rememberSelection(values as Partial<AddGroupMembersValues>)}
            >
                <Form.FormItem
                    label="用户"
                    name="users"
                    rules={[
                        {
                            validator: (value) =>
                                Array.isArray(value) && value.length > 0
                                    ? true
                                    : { message: "请选择至少一个用户。", result: false },
                        },
                    ]}
                >
                    <Select
                        clearable
                        filterable
                        loading={searchQuery.isFetching}
                        minCollapsedNum={2}
                        multiple
                        onSearch={setKeyword}
                        options={options}
                        placeholder="搜索姓名或邮箱"
                    />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}

function userOptionLabel(user: ListedUser & { name: string }): string {
    const displayName = user.displayName?.trim() || user.name;
    const loginName = user.loginName?.trim() || user.name;
    const group = user.userGroupDisplayName?.trim() || "未分组";

    return `${displayName} (@${loginName}) · ${group}`;
}
