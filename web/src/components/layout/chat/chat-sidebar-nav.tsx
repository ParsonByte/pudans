import { useEffect, useMemo } from "react";

import { MessageCirclePlus } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { withoutChatMenus } from "@/components/layout/nav-filter";
import { NavGroup } from "@/components/layout/nav-group";
import type { NavGroup as NavGroupData, NavUrl } from "@/components/layout/types";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";

const NEW_CHAT_URL: NavUrl = "/";

export function ChatSidebarNav({ navGroups }: { navGroups: NavGroupData[] }) {
  const router = useRouter();
  const pathname = usePathname();
  const groups = useMemo(() => withoutChatMenus(navGroups), [navGroups]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        router.push(NEW_CHAT_URL);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [router]);

  return (
    <>
      <SidebarGroup>
        <SidebarMenu className="gap-0.5">
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              isActive={pathname === NEW_CHAT_URL}
              className="h-10 border bg-background"
            >
              <Link href={NEW_CHAT_URL}>
                <MessageCirclePlus className="size-5" />
                <span>新建会话</span>
                <KbdGroup className="ms-auto">
                  <Kbd>⌘</Kbd>
                  <Kbd>K</Kbd>
                </KbdGroup>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
      {groups.map((group) => (
        <NavGroup key={group.title} {...group} />
      ))}
    </>
  );
}
