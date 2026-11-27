"use client";

import { usePathname, useSearchParams } from "next/navigation";

import { config } from "@fortawesome/fontawesome-svg-core";
import { faFolder } from "@fortawesome/free-regular-svg-icons";
import { faEllipsis, faPlus } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { GraphicDesignTwo } from "@icon-park/react";
import { useQuery } from "@tanstack/react-query";
import { LayoutGridCirclesIcon, SparklesIcon } from "lucide-react";
import { ChatBubbleHistoryIcon, ChatIcon, ExploreIcon, LightbulbIcon } from "tdesign-icons-react";
import { Button, Menu, Skeleton } from "tdesign-react";

import type { AppShellSidebarProps } from "@/components/layouts";
import { SIDE_NAVIGATION_ID } from "@/components/layouts/app-shell-types";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import {
    INDEX_LEGACY_PAGE_PARAM,
    INDEX_NAV_GROUP,
    INDEX_PAGE_KEYS,
    INDEX_SESSION_PARAM,
    indexPageHref,
    isIndexPageKey,
    resolveIndexPage,
} from "@/routes/(_workspace)/(_index)/paths";

import {
    CONVERSATION_SESSIONS_QUERY_KEY,
    CONVERSATION_SUMMARY_QUERY_KEY,
    getMyConversationSummary,
    listMyConversationSessions,
} from "../composables/_conversation";
import { conversationSidebarTime } from "./conversation/conversation-utils";

config.autoAddCss = false;

const { MenuItem, SubMenu } = Menu;
const SIDEBAR_WIDTH = "232px";
const SIDEBAR_COLLAPSED_WIDTH = "48px";

export function IndexSidebar({ collapsed, isDark, onNavigate }: AppShellSidebarProps) {
    const pathname = usePathname();
    const searchParams = useSearchParams();
    const activePage = resolveIndexPage(
        pathname,
        searchParams.get(INDEX_NAV_GROUP),
        searchParams.get(INDEX_LEGACY_PAGE_PARAM),
    );
    const openSessionIds = activePage === INDEX_PAGE_KEYS.conversation ? searchParams.getAll(INDEX_SESSION_PARAM) : [];
    const activeSessionId = openSessionIds[0];
    const sessionsQuery = useQuery({
        queryFn: () => listMyConversationSessions(20),
        queryKey: CONVERSATION_SESSIONS_QUERY_KEY,
    });
    const summaryQuery = useQuery({
        queryFn: getMyConversationSummary,
        queryKey: CONVERSATION_SUMMARY_QUERY_KEY,
    });
    const sessions = sessionsQuery.data ?? [];
    const sessionCount = summaryQuery.data?.total ?? sessions.length;

    const openConversation = (sessionId?: string) => {
        const ids = sessionId ? [sessionId, ...openSessionIds.filter((id) => id !== sessionId)] : openSessionIds;

        onNavigate(indexPageHref(INDEX_PAGE_KEYS.conversation, ids));
    };

    return (
        <aside
            className="app-shell__aside index-sidebar"
            data-collapsed={collapsed ? "true" : "false"}
            id={SIDE_NAVIGATION_ID}
        >
            <div className="index-sidebar__layout">
                <nav aria-label="DANS 导航" className="index-sidebar__navigation">
                    <Menu
                        collapsed={collapsed}
                        onChange={(value) => {
                            const page = String(value);

                            if (isIndexPageKey(page)) {
                                onNavigate(indexPageHref(page));
                            }
                        }}
                        theme={isDark ? "dark" : "light"}
                        value={activePage}
                        width={[SIDEBAR_WIDTH, SIDEBAR_COLLAPSED_WIDTH]}
                    >
                        <MenuItem icon={<SparklesIcon className="t-icon" />} value={INDEX_PAGE_KEYS.create}>
                            创作
                        </MenuItem>
                        <MenuItem icon={<ExploreIcon />} value={INDEX_PAGE_KEYS.explore}>
                            探索
                        </MenuItem>
                        <MenuItem icon={<GraphicDesignTwo className="t-icon" size={20} />} value={INDEX_PAGE_KEYS.dans}>
                            DANS
                        </MenuItem>
                        <MenuItem icon={<LayoutGridCirclesIcon className="t-icon" />} value={INDEX_PAGE_KEYS.aiTools}>
                            AI 工具
                        </MenuItem>
                        <MenuItem
                            icon={<FontAwesomeIcon className="t-icon" icon={faFolder} />}
                            value={INDEX_PAGE_KEYS.assets}
                        >
                            资产和主体
                        </MenuItem>
                        <SubMenu
                            icon={<FontAwesomeIcon className="t-icon" icon={faEllipsis} />}
                            title="更多"
                            value="more"
                        >
                            <MenuItem icon={<LightbulbIcon />} value={INDEX_PAGE_KEYS.deepUnderstanding}>
                                深度理解
                            </MenuItem>
                        </SubMenu>
                    </Menu>
                </nav>

                <section aria-label="对话管理" className="index-sidebar__conversations">
                    <div className="flex items-center justify-between gap-2 px-2">
                        <div className="flex min-w-0 items-center gap-2">
                            <button
                                aria-label="打开会话页"
                                className={`flex items-center gap-2 text-xs font-semibold ${
                                    activePage === INDEX_PAGE_KEYS.conversation
                                        ? "text-[var(--td-brand-color)]"
                                        : "text-[var(--td-text-color-secondary)]"
                                }`}
                                onClick={() => openConversation()}
                                type="button"
                            >
                                <ChatIcon size={16} />
                                对话
                            </button>
                            <span className="text-xs text-[var(--td-text-color-placeholder)]">
                                {summaryQuery.isPending ? "…" : sessionCount}
                            </span>
                        </div>
                        <button
                            aria-label="新建会话"
                            className="index-sidebar__conversation-add"
                            onClick={() => onNavigate(indexPageHref(INDEX_PAGE_KEYS.conversation))}
                            title="新建会话"
                            type="button"
                        >
                            <FontAwesomeIcon icon={faPlus} />
                        </button>
                    </div>
                    {sessionsQuery.isPending ? (
                        <div className="index-sidebar__conversation-list" aria-label="正在加载会话">
                            {[0, 1, 2].map((index) => (
                                <Skeleton key={index} animation="gradient" theme="text" />
                            ))}
                        </div>
                    ) : sessionsQuery.isError ? (
                        <div className="index-sidebar__conversation-error">
                            <span>{consoleErrorMessage(sessionsQuery.error)}</span>
                            <Button onClick={() => void sessionsQuery.refetch()} size="small" variant="text">
                                重试
                            </Button>
                        </div>
                    ) : sessions.length === 0 ? (
                        <div className="index-sidebar__conversation-empty">
                            <ChatBubbleHistoryIcon className="text-[var(--td-text-color-placeholder)]" size={24} />
                            <span className="mt-2 block text-sm text-[var(--td-text-color-secondary)]">暂无对话</span>
                            <span className="mt-1 block text-xs text-[var(--td-text-color-placeholder)]">
                                开始 AI 对话后会显示在这里
                            </span>
                        </div>
                    ) : (
                        <div className="index-sidebar__conversation-list">
                            {sessions.map((session) => (
                                <button
                                    aria-current={
                                        activePage === INDEX_PAGE_KEYS.conversation && activeSessionId === session.id
                                            ? "page"
                                            : undefined
                                    }
                                    className={`index-sidebar__conversation-item ${
                                        activePage === INDEX_PAGE_KEYS.conversation && activeSessionId === session.id
                                            ? "index-sidebar__conversation-item--active"
                                            : ""
                                    }`}
                                    key={session.id}
                                    onClick={() => openConversation(session.id)}
                                    title={session.title || "未命名会话"}
                                    type="button"
                                >
                                    <span className="index-sidebar__conversation-title">
                                        {session.title?.trim() || "未命名会话"}
                                    </span>
                                    <span className="index-sidebar__conversation-meta">
                                        {conversationSidebarTime(session.lastMessageAt ?? session.updatedAt)}
                                    </span>
                                </button>
                            ))}
                        </div>
                    )}
                </section>
            </div>
        </aside>
    );
}
