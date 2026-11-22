"use client";

import { Modal } from "antd";

import { useIsClient } from "@/hooks/utils/use-is-client";
import { AUTH_GLASS_CLASS } from "@/modules/_auth/styles/glass";

import { LegalView } from "./index";
import type { LegalDocumentKey } from "./legal-content";

type LegalDialogProps = {
    activeTab: LegalDocumentKey;
    onClose: () => void;
    onTabChange: (tab: LegalDocumentKey) => void;
    open: boolean;
};

export function LegalDialog({ activeTab, onClose, onTabChange, open }: LegalDialogProps) {
    const isClient = useIsClient();

    if (!isClient) {
        return null;
    }

    return (
        <Modal
            centered
            classNames={{ container: AUTH_GLASS_CLASS }}
            destroyOnHidden
            footer={null}
            mask={{ blur: true }}
            onCancel={onClose}
            open={open}
            width={800}
        >
            <LegalView activeTab={activeTab} onTabChange={onTabChange} />
        </Modal>
    );
}
