"use client";

import { useEffect } from "react";

import { X } from "lucide-react";

import { dismissDreamToast } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

const TONE_CLASS = {
    info: styles.toastInfo,
    success: styles.toastSuccess,
    warning: styles.toastWarning,
};

function ToastItem({ id, message, tone }: { id: string; message: string; tone: keyof typeof TONE_CLASS }) {
    useEffect(() => {
        const timer = window.setTimeout(() => dismissDreamToast(id), 4200);

        return () => window.clearTimeout(timer);
    }, [id]);

    return (
        <div className={`${styles.toast} ${TONE_CLASS[tone]}`} role="status">
            <span>{message}</span>
            <button
                aria-label="关闭提示"
                className={styles.panelClose}
                onClick={() => dismissDreamToast(id)}
                type="button"
            >
                <X aria-hidden size={12} />
            </button>
        </div>
    );
}

export function ToastCenter() {
    const toasts = useDreamStore((state) => state.toasts);

    return (
        <div className={styles.toastCenter}>
            {toasts.map((toast) => (
                <ToastItem id={toast.id} key={toast.id} message={toast.message} tone={toast.tone} />
            ))}
        </div>
    );
}
