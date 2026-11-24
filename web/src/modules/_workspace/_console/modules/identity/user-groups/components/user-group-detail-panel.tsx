"use client";

import { useMemo, useState } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import { Tabs } from "tdesign-react";
import type { TabValue } from "tdesign-react";

import { useRolesQuery } from "../../roles/composables/_role-queries";
import {
    buildUserGroupRolePayload,
    isBuiltinUserGroup,
    isSuperAdminsUserGroup,
    userGroupRoleNames,
} from "../composables/_user-group";
import { useUpdateUserGroup } from "../composables/_user-group-actions";
import { useUserGroupMembersQuery } from "../composables/_user-group-queries";
import { AddUserGroupMembersModal } from "./add-user-group-members-modal";
import { AddUserGroupRolesModal } from "./add-user-group-roles-modal";
import { UserGroupMembers } from "./user-group-members";
import { UserGroupRoles } from "./user-group-roles";
import { UserGroupSummary } from "./user-group-summary";

type UserGroupDetailPanelProps = {
    canDelete: boolean;
    canGroupMembers: boolean;
    canUpdate: boolean;
    canViewUsers: boolean;
    group: UserGroup;
    onDelete: (group: UserGroup) => void;
    onEdit: (group: UserGroup) => void;
    onRefresh: () => void;
    workspace: string;
};

export function UserGroupDetailPanel({
    canDelete,
    canGroupMembers,
    canUpdate,
    canViewUsers,
    group,
    onDelete,
    onEdit,
    onRefresh,
    workspace,
}: UserGroupDetailPanelProps) {
    const rolesQuery = useRolesQuery();
    const membersQuery = useUserGroupMembersQuery(group.metadata.name);
    const updateGroup = useUpdateUserGroup();
    const [activeTab, setActiveTab] = useState<TabValue>("members");
    const [membersOpen, setMembersOpen] = useState(false);
    const [rolesOpen, setRolesOpen] = useState(false);
    const roleNames = useMemo(() => userGroupRoleNames(group), [group]);
    const memberNames = useMemo(
        () =>
            new Set(
                (membersQuery.data ?? []).map((member) => member.name).filter((name): name is string => Boolean(name)),
            ),
        [membersQuery.data],
    );
    const superAdmins = isSuperAdminsUserGroup(group);
    const builtin = isBuiltinUserGroup(group);
    const canManageMembers = canGroupMembers && !superAdmins;
    const canManageRoles = canUpdate && !superAdmins;
    const canEditProfile = canUpdate && !builtin;
    const canDeleteGroup = canDelete && !builtin;

    const removeRole = async (roleName: string) => {
        await updateGroup.mutateAsync({
            affectsPermissions: true,
            name: group.metadata.name,
            successMessage: "角色已移除。",
            userGroup: buildUserGroupRolePayload(
                group,
                roleNames.filter((name) => name !== roleName),
            ),
        });
    };

    const items = [
        {
            label: "成员",
            panel: (
                <div className="pt-4">
                    <UserGroupMembers
                        canManage={canManageMembers}
                        canViewUsers={canViewUsers}
                        error={membersQuery.error}
                        groupName={group.metadata.name}
                        loading={membersQuery.isPending}
                        members={membersQuery.data}
                        onAdd={() => setMembersOpen(true)}
                        onRetry={() => void membersQuery.refetch()}
                        workspace={workspace}
                    />
                </div>
            ),
            value: "members",
        },
        {
            label: "角色",
            panel: (
                <div className="pt-4">
                    <UserGroupRoles
                        canManage={canManageRoles}
                        group={group}
                        onAdd={() => setRolesOpen(true)}
                        onRemove={removeRole}
                        onRetry={() => void rolesQuery.refetch()}
                        roles={rolesQuery.data ?? []}
                        rolesError={rolesQuery.error}
                        rolesPending={rolesQuery.isPending}
                    />
                </div>
            ),
            value: "roles",
        },
    ];

    return (
        <>
            <div className="min-w-0">
                <div className="border-b border-[var(--td-component-border)] px-4 py-5 lg:px-6">
                    <UserGroupSummary
                        canDelete={canDeleteGroup}
                        canEdit={canEditProfile}
                        group={group}
                        memberCount={membersQuery.data?.length}
                        onDelete={() => onDelete(group)}
                        onEdit={() => onEdit(group)}
                        onRefresh={onRefresh}
                        roleCount={roleNames.length}
                    />
                </div>
                <div className="px-4 pt-5 pb-6 lg:px-6">
                    <Tabs list={items} onChange={setActiveTab} value={activeTab} />
                </div>
            </div>
            {membersOpen ? (
                <AddUserGroupMembersModal
                    groupName={group.metadata.name}
                    memberNames={memberNames}
                    onClose={() => setMembersOpen(false)}
                />
            ) : null}
            {rolesOpen ? (
                <AddUserGroupRolesModal
                    group={group}
                    onClose={() => setRolesOpen(false)}
                    onSaved={() => setRolesOpen(false)}
                    roles={rolesQuery.data ?? []}
                />
            ) : null}
        </>
    );
}
