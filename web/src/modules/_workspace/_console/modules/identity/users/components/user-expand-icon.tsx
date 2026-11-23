"use client";

import type { MouseEventHandler } from "react";

import { Button } from "antd";
import { ChevronRight } from "lucide-react";

type UserExpandIconProps = {
    expandable: boolean;
    expanded: boolean;
    onClick: MouseEventHandler<HTMLElement>;
};

export function UserExpandIcon({ expandable, expanded, onClick }: UserExpandIconProps) {
    if (!expandable) {
        return null;
    }

    return (
        <Button
            aria-label={expanded ? "收起行" : "展开行"}
            icon={<ChevronRight className={`transition-transform ${expanded ? "rotate-90" : ""}`} size={14} />}
            onClick={onClick}
            size="small"
            type="text"
        />
    );
}
