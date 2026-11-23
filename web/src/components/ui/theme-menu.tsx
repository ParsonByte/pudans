"use client";

import type { CSSProperties, FocusEvent as ReactFocusEvent, KeyboardEvent as ReactKeyboardEvent } from "react";
import { useEffect, useId, useRef, useState } from "react";

import { useTheme } from "next-themes";
import { CheckIcon, MoonIcon, SunnyIcon } from "tdesign-icons-react";
import { Button, Popup } from "tdesign-react";

const THEME_ITEMS = [
    { label: "浅色", value: "light" },
    { label: "深色", value: "dark" },
    { label: "跟随系统", value: "system" },
] as const;

type ThemeMenuProps = {
    buttonClassName?: string;
    buttonStyle?: CSSProperties;
};

function getMenuOptions(menu: HTMLElement | null): HTMLElement[] {
    return menu ? [...menu.querySelectorAll<HTMLElement>('[role="menuitemradio"]')] : [];
}

export function ThemeMenu({ buttonClassName, buttonStyle }: ThemeMenuProps) {
    const { resolvedTheme, setTheme, theme } = useTheme();
    const [open, setOpen] = useState(false);
    const menuId = useId();
    const triggerRef = useRef<HTMLElement>(null);

    useEffect(() => {
        if (!open) {
            return;
        }

        let frame = 0;
        let attempts = 0;

        const focusSelectedOption = () => {
            const options = getMenuOptions(document.getElementById(menuId));
            const selectedIndex = THEME_ITEMS.findIndex((item) => item.value === theme);
            const target = options[selectedIndex >= 0 ? selectedIndex : 0];

            target?.focus();

            if ((!target || document.activeElement !== target) && attempts < 30) {
                attempts += 1;
                frame = window.requestAnimationFrame(focusSelectedOption);
            }
        };

        frame = window.requestAnimationFrame(focusSelectedOption);

        return () => window.cancelAnimationFrame(frame);
    }, [menuId, open, theme]);

    const selectTheme = (value: string) => {
        setTheme(value);
        setOpen(false);
        triggerRef.current?.focus();
    };

    const handleMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
        if (event.key === "Escape") {
            event.preventDefault();
            setOpen(false);
            triggerRef.current?.focus();
            return;
        }

        if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
            return;
        }

        event.preventDefault();

        const options = getMenuOptions(event.currentTarget);

        if (!options.length) {
            return;
        }

        const currentIndex = options.findIndex((option) => option === document.activeElement);
        const nextIndex =
            event.key === "Home"
                ? 0
                : event.key === "End"
                  ? options.length - 1
                  : (currentIndex + (event.key === "ArrowDown" ? 1 : -1) + options.length) % options.length;

        options[nextIndex]?.focus();
    };

    const handleMenuBlur = (event: ReactFocusEvent<HTMLDivElement>) => {
        const next = event.relatedTarget;

        if (!(next instanceof Node) || !event.currentTarget.contains(next)) {
            setOpen(false);
        }
    };

    return (
        <Popup
            attach="body"
            content={
                <div
                    aria-label="主题"
                    aria-orientation="vertical"
                    className="theme-menu"
                    id={menuId}
                    onBlur={handleMenuBlur}
                    onKeyDown={handleMenuKeyDown}
                    role="menu"
                >
                    {THEME_ITEMS.map((item) => {
                        const selected = theme === item.value;

                        return (
                            <Button
                                aria-checked={selected}
                                block
                                className="theme-menu__item"
                                key={item.value}
                                onClick={() => selectTheme(item.value)}
                                role="menuitemradio"
                                size="small"
                                type="button"
                                variant="text"
                            >
                                <span className="theme-menu__option">
                                    <span>{item.label}</span>
                                    {selected ? <CheckIcon className="theme-menu__check" size={16} /> : null}
                                </span>
                            </Button>
                        );
                    })}
                </div>
            }
            destroyOnClose
            onVisibleChange={setOpen}
            placement="bottom-right"
            showArrow={false}
            trigger="click"
            visible={open}
        >
            <Button
                aria-controls={menuId}
                aria-expanded={open}
                aria-haspopup="menu"
                aria-label="切换主题"
                className={buttonClassName ? `relative ${buttonClassName}` : "relative"}
                icon={resolvedTheme === "dark" ? <MoonIcon /> : <SunnyIcon />}
                ref={triggerRef}
                shape="square"
                style={buttonStyle}
                type="button"
                variant="text"
            />
        </Popup>
    );
}
