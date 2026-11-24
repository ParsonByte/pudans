"use client";

import { useRouter } from "next/navigation";

import { Undo2 } from "lucide-react";

import { INDEX_DREAM_SPACE_HREF } from "@/routes/(_workspace)/(_index)/paths";

import styles from "../../styles/dream-space.module.css";

export function PortalBackButton({
    className = styles.secondaryButton,
    label = "返回幻境",
}: {
    className?: string;
    label?: string;
}) {
    const router = useRouter();

    return (
        <button className={className} onClick={() => router.push(INDEX_DREAM_SPACE_HREF)} type="button">
            <Undo2 aria-hidden size={13} />
            {label}
        </button>
    );
}
