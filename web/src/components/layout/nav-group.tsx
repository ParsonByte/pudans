import type { ReactNode } from "react";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";

import type {
  BaseNavItem,
  NavCollapsible,
  NavGroup as NavGroupData,
  NavItem,
  NavLink,
} from "./types";

export function NavGroup({ title, items }: NavGroupData) {
  const { state, isMobile } = useSidebar();
  const href = usePathname();

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{title}</SidebarGroupLabel>
      <SidebarMenu>
        {items.map((item) => {
          const key = `${item.title}-${item.url}`;

          if (!item.items) {
            return <SidebarMenuLink key={key} item={item} href={href} />;
          }

          if (state === "collapsed" && !isMobile) {
            return <SidebarMenuCollapsedDropdown key={key} item={item} href={href} />;
          }

          return <SidebarMenuCollapsible key={key} item={item} href={href} />;
        })}
      </SidebarMenu>
    </SidebarGroup>
  );
}

function NavBadge({ children }: { children: ReactNode }) {
  return <Badge className="px-1 py-0 text-xs">{children}</Badge>;
}

function NavItemContent({ item }: { item: BaseNavItem }) {
  return (
    <>
      {item.icon && <item.icon />}
      <span>{item.title}</span>
      {item.badge && <NavBadge>{item.badge}</NavBadge>}
    </>
  );
}

function SidebarMenuLink({ item, href }: { item: NavLink; href: string }) {
  const { setOpenMobile } = useSidebar();

  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={checkIsActive(href, item)} tooltip={item.title}>
        <Link href={item.url} onClick={() => setOpenMobile(false)}>
          <NavItemContent item={item} />
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function SidebarMenuCollapsible({ item, href }: { item: NavCollapsible; href: string }) {
  const { setOpenMobile } = useSidebar();

  return (
    <Collapsible
      asChild
      defaultOpen={checkIsActive(href, item, true)}
      className="group/collapsible"
    >
      <SidebarMenuItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuButton tooltip={item.title}>
            <NavItemContent item={item} />
            <ChevronRight className="ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 rtl:rotate-180" />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <CollapsibleContent className="CollapsibleContent">
          <SidebarMenuSub>
            {item.items.map((subItem) => (
              <SidebarMenuSubItem key={subItem.title}>
                <SidebarMenuSubButton asChild isActive={checkIsActive(href, subItem)}>
                  <Link href={subItem.url} onClick={() => setOpenMobile(false)}>
                    <NavItemContent item={subItem} />
                  </Link>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  );
}

function SidebarMenuCollapsedDropdown({ item, href }: { item: NavCollapsible; href: string }) {
  return (
    <SidebarMenuItem>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <SidebarMenuButton tooltip={item.title} isActive={checkIsActive(href, item)}>
            <NavItemContent item={item} />
            <ChevronRight className="ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent side="right" align="start" sideOffset={4}>
          <DropdownMenuLabel>
            {item.title} {item.badge ? `(${item.badge})` : ""}
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {item.items.map((subItem) => (
            <DropdownMenuItem key={`${subItem.title}-${subItem.url}`} asChild>
              <Link
                href={subItem.url}
                className={cn(checkIsActive(href, subItem) && "bg-secondary")}
              >
                {subItem.icon && <subItem.icon />}
                <span className="max-w-52 text-wrap">{subItem.title}</span>
                {subItem.badge && <span className="ms-auto text-xs">{subItem.badge}</span>}
              </Link>
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  );
}

function checkIsActive(href: string, item: NavItem, mainNav = false): boolean {
  return (
    href === item.url ||
    href.split("?")[0] === item.url ||
    Boolean(item.items?.some((subItem) => subItem.url === href)) ||
    (mainNav && href.split("/")[1] !== "" && href.split("/")[1] === item?.url?.split("/")[1])
  );
}
