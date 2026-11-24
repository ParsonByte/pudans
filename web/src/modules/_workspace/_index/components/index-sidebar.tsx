"use client";

import { config } from "@fortawesome/fontawesome-svg-core";
import { faFolder } from "@fortawesome/free-regular-svg-icons";
import { faEllipsis } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { GraphicDesignTwo } from "@icon-park/react";
import { LayoutGridCirclesIcon, LineSquiggleIcon, SparklesIcon } from "lucide-react";
import { ChatBubbleHistoryIcon, ChatIcon, ExploreIcon, LightbulbIcon } from "tdesign-icons-react";
import { Menu } from "tdesign-react";

import type { AppShellSidebarProps } from "@/components/layouts";
import { SIDE_NAVIGATION_ID } from "@/components/layouts/app-shell-types";

config.autoAddCss = false;

const { MenuItem, SubMenu } = Menu;
const SIDEBAR_WIDTH = "232px";
const SIDEBAR_COLLAPSED_WIDTH = "48px";

export function IndexSidebar({ collapsed, isDark }: AppShellSidebarProps) {
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
                        defaultValue="create"
                        theme={isDark ? "dark" : "light"}
                        width={[SIDEBAR_WIDTH, SIDEBAR_COLLAPSED_WIDTH]}
                    >
                        <MenuItem icon={<SparklesIcon className="t-icon" />} value="create">
                            创作
                        </MenuItem>
                        <MenuItem icon={<ExploreIcon />} value="explore">
                            探索
                        </MenuItem>
                        <MenuItem icon={<GraphicDesignTwo className="t-icon" size={20} />} value="dans">
                            DANS
                        </MenuItem>
                        <MenuItem icon={<LayoutGridCirclesIcon className="t-icon" />} value="ai-tools">
                            AI 工具
                        </MenuItem>
                        <MenuItem icon={<FontAwesomeIcon className="t-icon" icon={faFolder} />} value="assets">
                            资产和主体
                        </MenuItem>
                        <SubMenu
                            icon={<FontAwesomeIcon className="t-icon" icon={faEllipsis} />}
                            title="更多"
                            value="more"
                        >
                            <MenuItem icon={<LightbulbIcon />} value="deep-understanding">
                                深度理解
                            </MenuItem>
                            <MenuItem icon={<LineSquiggleIcon className="t-icon" />} value="dream-space">
                                幻境空间
                            </MenuItem>
                        </SubMenu>
                    </Menu>
                </nav>

                <section aria-label="对话管理" className="index-sidebar__conversations">
                    <div className="flex items-center justify-between gap-2 px-2">
                        <span className="flex items-center gap-2 text-xs font-semibold text-[var(--td-text-color-secondary)]">
                            <ChatIcon size={16} />
                            对话
                        </span>
                        <span className="text-xs text-[var(--td-text-color-placeholder)]">0</span>
                    </div>
                    <div className="index-sidebar__conversation-empty">
                        <ChatBubbleHistoryIcon className="text-[var(--td-text-color-placeholder)]" size={24} />
                        <span className="mt-2 block text-sm text-[var(--td-text-color-secondary)]">暂无对话</span>
                        <span className="mt-1 block text-xs text-[var(--td-text-color-placeholder)]">
                            开始 AI 对话后会显示在这里
                        </span>
                    </div>
                </section>
            </div>
        </aside>
    );
}
