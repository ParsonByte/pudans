"use client";

import { useState } from "react";

import { ArrowUpRight } from "lucide-react";

import { pushDreamToast } from "../../stores/dream-store";
import styles from "../../styles/dream-space.module.css";
import { openExternalLink } from "../../world/external-link-service";

export function NewTabLauncher({ label, url }: { label: string; url: string }) {
    const [launched, setLaunched] = useState(false);

    return (
        <button
            className={styles.primaryButton}
            onClick={() => {
                const result = openExternalLink(url);

                if (result.allowed) {
                    setLaunched(true);
                    window.setTimeout(() => setLaunched(false), 900);
                } else {
                    pushDreamToast(result.reason, "warning");
                }
            }}
            type="button"
        >
            <ArrowUpRight aria-hidden size={13} />
            {launched ? "已打开" : `打开 ${label}`}
        </button>
    );
}
