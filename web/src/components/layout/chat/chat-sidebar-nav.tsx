import { useState } from "react";

import { Link, useLocation } from "@tanstack/react-router";
import {
  Brain,
  ChevronRight,
  CircleCheck,
  Ellipsis,
  Frame,
  Lightbulb,
  MessageCirclePlus,
  PenLine,
  ScanSearch,
  Sparkles,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";

import type { NavUrl } from "../types";

const NEW_CHAT_URL: NavUrl = "/";

const MAIN_ITEMS = [
  { label: "我的 DANS", icon: Sparkles },
  { label: "记忆", icon: Brain },
  { label: "任务", icon: CircleCheck },
  { label: "灵感库", icon: Lightbulb },
  { label: "无限画布", icon: Frame, badge: "即将上线" },
] as const;

const MORE_ITEMS = [
  { label: "深度解析", icon: ScanSearch },
  { label: "贡献灵感", icon: PenLine },
] as const;

export function ChatSidebarNav() {
  const [expanded, setExpanded] = useState(false);
  const pathname = useLocation({ select: (location) => location.pathname });

  return (
    <SidebarGroup>
      <SidebarMenu className="gap-0.5">
        <SidebarMenuItem>
          <SidebarMenuButton
            asChild
            isActive={pathname === NEW_CHAT_URL}
            className="h-10 border bg-background"
          >
            <Link to={NEW_CHAT_URL}>
              <MessageCirclePlus className="size-5" />
              <span>新建会话</span>
              <KbdGroup className="ms-auto">
                <Kbd>⌘</Kbd>
                <Kbd>K</Kbd>
              </KbdGroup>
            </Link>
          </SidebarMenuButton>
        </SidebarMenuItem>
        {MAIN_ITEMS.map((item) => (
          <SidebarMenuItem key={item.label}>
            <SidebarMenuButton className="h-10">
              <item.icon className="size-5" />
              <span>{item.label}</span>
              {"badge" in item && item.badge ? (
                <Badge className="ms-auto rounded-md bg-indigo-500/15 px-1.5 text-[10px] font-medium text-indigo-500 dark:text-indigo-400">
                  {item.badge}
                </Badge>
              ) : null}
            </SidebarMenuButton>
          </SidebarMenuItem>
        ))}
        <SidebarMenuItem>
          <HoverCard openDelay={120} closeDelay={120}>
            <HoverCardTrigger asChild>
              <SidebarMenuButton className="h-10" onClick={() => setExpanded((value) => !value)}>
                <Ellipsis className="size-5" />
                <span>{expanded ? "收起" : "更多"}</span>
                {!expanded ? (
                  <ChevronRight className="ms-auto size-4 text-muted-foreground" />
                ) : null}
              </SidebarMenuButton>
            </HoverCardTrigger>
            <HoverCardContent side="right" align="start" sideOffset={8} className="w-44 p-1">
              {MORE_ITEMS.map((item) => (
                <Button
                  key={item.label}
                  type="button"
                  variant="ghost"
                  className="h-9 w-full justify-start gap-2 px-2"
                >
                  <item.icon className="size-4" />
                  <span>{item.label}</span>
                </Button>
              ))}
            </HoverCardContent>
          </HoverCard>
        </SidebarMenuItem>
        {expanded
          ? MORE_ITEMS.map((item) => (
              <SidebarMenuItem key={item.label}>
                <SidebarMenuButton className="h-10 text-muted-foreground">
                  <item.icon className="size-5" />
                  <span>{item.label}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ))
          : null}
      </SidebarMenu>
    </SidebarGroup>
  );
}
