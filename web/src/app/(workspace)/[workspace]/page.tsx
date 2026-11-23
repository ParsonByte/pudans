"use client";

import { Suspense, useEffect } from "react";

import { useParams } from "next/navigation";

import { Empty, Loading } from "tdesign-react";

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
                content: "工作区不可用。",
            });
            return;
        }

        if (!selection) {
            prompt.warning({
                content: "工作区暂无页面。",
            });
            return;
        }

        if (!View) {
            prompt.warning({
                content: "页面尚未接入。",
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
        return (
            <div className="flex min-h-48 items-center justify-center">
                <Loading />
            </div>
        );
    }

    if (dynamicMenu.error) {
        return <Empty description="工作区不可用" />;
    }

    if (dynamicMenu.notFound || !dynamicMenu.workspaceKey) {
        return <Empty description="工作区不可用" />;
    }

    if (!selection) {
        return <Empty description="暂无页面" />;
    }

    if (!View) {
        return <Empty description="页面尚未接入" />;
    }

    return <View />;
}

export default function WorkspacePage() {
    return (
        <Suspense
            fallback={
                <div className="flex min-h-48 items-center justify-center">
                    <Loading />
                </div>
            }
        >
            <WorkspacePageContent />
        </Suspense>
    );
}
