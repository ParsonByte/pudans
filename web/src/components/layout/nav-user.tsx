import { useState } from "react";

import { ChevronsUpDown, Link2, LogOut, ShieldCheck, UserRound } from "lucide-react";
import Link from "next/link";

import { SignOutDialog } from "@/components/sign-out-dialog";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

import type { NavUrl, User } from "./types";

const PROFILE_URL: NavUrl = "/profile";
const SECURITY_URL: NavUrl = "/security";
const IDENTITIES_URL: NavUrl = "/identity-providers";

type NavUserProps = {
  user: User;
  onSignOut?: () => void;
};

function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export function NavUser({ user, onSignOut }: NavUserProps) {
  const { isMobile } = useSidebar();
  const [signOutOpen, setSignOutOpen] = useState(false);
  const initials = getInitials(user.name);

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <Avatar className="h-8 w-8">
                {user.avatar ? <AvatarImage src={user.avatar} alt={user.name} /> : null}
                <AvatarFallback className="">{initials}</AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-start text-sm leading-tight">
                <span className="truncate font-semibold">{user.name}</span>
                <span className="truncate text-xs">@{user.email}</span>
              </div>
              <ChevronsUpDown className="ms-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-start text-sm">
                <Avatar className="h-8 w-8">
                  {user.avatar ? <AvatarImage src={user.avatar} alt={user.name} /> : null}
                  <AvatarFallback className="">{initials}</AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-start text-sm leading-tight">
                  <span className="truncate font-semibold">{user.name}</span>
                  <span className="truncate text-xs">@{user.email}</span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem asChild>
                <Link href={PROFILE_URL}>
                  <UserRound />
                  个人设置
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href={SECURITY_URL}>
                  <ShieldCheck />
                  安全中心
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href={IDENTITIES_URL}>
                  <Link2 />
                  身份提供商
                </Link>
              </DropdownMenuItem>
            </DropdownMenuGroup>
            {onSignOut ? (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => setSignOutOpen(true)}>
                  <LogOut />
                  退出登录
                </DropdownMenuItem>
              </>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
        {onSignOut ? (
          <SignOutDialog open={signOutOpen} onOpenChange={setSignOutOpen} onSignOut={onSignOut} />
        ) : null}
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
