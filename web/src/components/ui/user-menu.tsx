"use client";

import { useState } from "react";

import { useRouter } from "next/navigation";

import { ChevronDownIcon, PoweroffIcon, UserCircleIcon } from "tdesign-icons-react";
import { Button, Dropdown, type DropdownOption } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { signOut } from "@/modules/_auth/composables/_auth-login";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { useConsoleSession } from "@/modules/_workspace/_console/composables/_session";
import { UserAvatar } from "@/modules/_workspace/_console/modules/identity/users/components/user-avatar";

const PERSONAL_CENTER_PATH = "/workspace/uc?account=profile";
const MENU_OPTIONS: DropdownOption[] = [
    { content: "个人中心", prefixIcon: <UserCircleIcon />, value: "profile" },
    { content: "退出登录", prefixIcon: <PoweroffIcon />, value: "logout" },
];

export function UserMenu() {
    const router = useRouter();
    const prompt = useMessagePrompt();
    const sessionQuery = useConsoleSession();
    const [signingOut, setSigningOut] = useState(false);
    const session = sessionQuery.data;
    const displayName = session?.displayName || session?.username || "当前用户";

    const handleMenuClick = async (item: DropdownOption) => {
        if (item.value === "profile") {
            router.push(PERSONAL_CENTER_PATH);
            return;
        }

        if (item.value !== "logout" || signingOut) {
            return;
        }

        setSigningOut(true);

        try {
            await signOut();
            window.location.replace("/sign-in");
        } catch (error) {
            setSigningOut(false);
            prompt.error({ content: consoleErrorMessage(error, "退出登录失败，请重试。") });
        }
    };

    return (
        <Dropdown
            minColumnWidth={152}
            onClick={(item) => void handleMenuClick(item)}
            options={MENU_OPTIONS}
            placement="bottom-right"
            popupProps={{ overlayClassName: "app-shell__user-menu-popup" }}
            trigger="click"
        >
            <Button
                aria-label={`账号菜单：${displayName}`}
                aria-haspopup="menu"
                className="app-shell__user-menu-trigger"
                disabled={signingOut}
                loading={signingOut}
                type="button"
                variant="text"
            >
                <span className="app-shell__user-menu-content">
                    <UserAvatar name={displayName} size={22} src={session?.avatar} />
                    <span className="app-shell__user-menu-name">{displayName}</span>
                    <ChevronDownIcon className="app-shell__user-menu-chevron" size={16} />
                </span>
            </Button>
        </Dropdown>
    );
}
