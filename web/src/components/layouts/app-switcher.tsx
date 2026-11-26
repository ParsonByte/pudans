"use client";

import { usePathname, useRouter } from "next/navigation";

import type { NavigationWorkspace } from "@pudans-dev/api-client";
import { MenuApplicationIcon } from "tdesign-icons-react";
import { Button, Dropdown, type DropdownOption } from "tdesign-react";

import { MenuIcon } from "@/components/ui";
import { useNavigationQuery } from "@/composables/useDynamicMenu";
import { workspaceBasePath } from "@/composables/workspace-navigation";

const WORKSPACE_PATH_PREFIX = "/workspace/";

function activeWorkspacePath(pathname: string): string | undefined {
    if (!pathname.startsWith(WORKSPACE_PATH_PREFIX)) {
        return undefined;
    }

    const [slug] = pathname.slice(WORKSPACE_PATH_PREFIX.length).split("/");

    return slug ? `${WORKSPACE_PATH_PREFIX}${slug}` : undefined;
}

function workspaceOptions(workspaces: NavigationWorkspace[], activePath?: string): DropdownOption[] {
    return workspaces
        .slice()
        .sort((left, right) => (left.order ?? 0) - (right.order ?? 0))
        .flatMap((workspace) => {
            const path = workspaceBasePath(workspace);

            if (!path) {
                return [];
            }

            return [
                {
                    active: path === activePath,
                    content: workspace.name ?? path,
                    prefixIcon: <MenuIcon icon={workspace.icon} size={18} />,
                    value: path,
                },
            ];
        });
}

export function AppSwitcher() {
    const router = useRouter();
    const pathname = usePathname();
    const navigationQuery = useNavigationQuery();
    const activePath = activeWorkspacePath(pathname);
    const options = workspaceOptions(navigationQuery.data?.workspaces ?? [], activePath);
    const handleClick = (item: DropdownOption) => {
        if (typeof item.value === "string" && item.value !== activePath) {
            router.push(item.value);
        }
    };

    return (
        <Dropdown
            disabled={options.length === 0}
            minColumnWidth={168}
            onClick={handleClick}
            options={options}
            placement="bottom-left"
            trigger="click"
        >
            <Button
                aria-label="应用切换"
                className="app-switcher__trigger"
                ghost
                icon={<MenuApplicationIcon size={24} />}
                shape="square"
                variant="text"
            />
        </Dropdown>
    );
}
