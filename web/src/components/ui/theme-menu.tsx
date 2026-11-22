"use client";

import type { CSSProperties } from "react";

import { Button, Dropdown } from "antd";
import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

const THEME_ITEMS = [
    { key: "light", label: "浅色" },
    { key: "dark", label: "深色" },
    { key: "system", label: "跟随系统" },
];

type ThemeMenuProps = {
    buttonClassName?: string;
    buttonStyle?: CSSProperties;
};

export function ThemeMenu({ buttonClassName, buttonStyle }: ThemeMenuProps) {
    const { theme, setTheme } = useTheme();

    return (
        <Dropdown
            menu={{
                items: THEME_ITEMS,
                onClick: ({ key }) => setTheme(key),
                selectable: true,
                selectedKeys: [theme ?? "system"],
            }}
            trigger={["click"]}
        >
            <Button
                aria-label="切换主题"
                className={buttonClassName ? `relative ${buttonClassName}` : "relative"}
                style={buttonStyle}
                type="text"
            >
                <Sun className="size-[1.2rem] scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
                <Moon className="absolute size-[1.2rem] scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
            </Button>
        </Dropdown>
    );
}
