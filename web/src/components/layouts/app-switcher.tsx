"use client";

import { MenuApplicationIcon } from "tdesign-icons-react";
import { Button, Popup } from "tdesign-react";

export function AppSwitcher() {
    return (
        <Popup
            content={<div className="app-switcher__panel" />}
            destroyOnClose
            placement="bottom-left"
            showArrow={false}
            trigger="hover"
        >
            <Button
                aria-label="应用切换"
                className="app-switcher__trigger"
                ghost
                icon={<MenuApplicationIcon size={24} />}
                shape="square"
                variant="text"
            />
        </Popup>
    );
}
