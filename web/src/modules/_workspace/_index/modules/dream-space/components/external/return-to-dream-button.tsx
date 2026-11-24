"use client";

import { useRouter } from "next/navigation";

import { Undo2 } from "lucide-react";

import { INDEX_DREAM_SPACE_HREF } from "@/routes/(_workspace)/(_index)/paths";

import styles from "../../styles/dream-space.module.css";

export function ReturnToDreamButton() {
    const router = useRouter();

    return (
        <button className={styles.primaryButton} onClick={() => router.push(INDEX_DREAM_SPACE_HREF)} type="button">
            <Undo2 aria-hidden size={13} />
            回到梦境
        </button>
    );
}
