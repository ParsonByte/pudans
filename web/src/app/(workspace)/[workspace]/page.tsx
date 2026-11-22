"use client";

import { Suspense, useEffect } from "react";

import { useParams } from "next/navigation";

import { Empty, Skeleton } from "antd";

import { useDynamicMenu } from "@/composables/useDynamicMenu";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { WORKSPACE_VIEWS } from "@/routes/(_workspace)";

function WorkspacePageContent() {
    const { workspace } = useParams<{ workspace: string }>();
    const dynamicMenu = useDynamicMenu(workspace);
    const prompt = useMessagePrompt();
    const selection = dynamicMenu.selection;
    const View =
        dynamicMenu.workspaceKey && selection
            ? WORKSPACE_VIEWS[dynamicMenu.workspaceKey]?.[selection.group.key]?.[selection.item.menuKey]
            : undefined;

    useEffect(() => {
        if (dynamicMenu.loading) {
            return;
        }

        if (dynamicMenu.error) {
            return;
        }

        if (dynamicMenu.notFound || !dynamicMenu.workspaceKey) {
            prompt.warning({
                content: `未找到 ${workspace} 工作区，或当前账号无权访问。`,
                key: "workspace-page-warning",
            });
            return;
        }

        if (!selection) {
            prompt.warning({
                content: "YAML 中没有返回可访问的菜单。",
                key: "workspace-page-warning",
            });
            return;
        }

        if (!View) {
            prompt.warning({
                content: `导航已返回 ${selection.group.key}=${selection.item.menuKey}，但前端视图注册表中还没有对应入口。`,
                key: "workspace-page-warning",
            });
        }
    }, [
        View,
        dynamicMenu.error,
        dynamicMenu.loading,
        dynamicMenu.notFound,
        dynamicMenu.workspaceKey,
        prompt,
        selection,
        workspace,
    ]);

    if (dynamicMenu.loading) {
        return <Skeleton active />;
    }

    if (dynamicMenu.error) {
        return <Empty description="工作区导航加载失败" />;
    }

    if (dynamicMenu.notFound || !dynamicMenu.workspaceKey) {
        return <Empty description={`未找到 ${workspace} 工作区，或当前账号无权访问。`} />;
    }

    if (!selection) {
        return <Empty description="工作区暂无可用页面" />;
    }

    if (!View) {
        return <Empty description="工作区页面尚未接入" />;
    }

    return <View />;
}

export default function WorkspacePage() {
    return (
        <Suspense fallback={<Skeleton active />}>
            <WorkspacePageContent />
        </Suspense>
    );
}
