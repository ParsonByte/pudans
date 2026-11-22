"use client";

import { Suspense } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import { Flex, Modal, Spin } from "antd";

import { useIsClient } from "@/hooks/utils/use-is-client";
import { ForgotPasswordView, LEGAL_KEYS, type LegalDocumentKey, LegalView, SignInView } from "@/modules/_auth";

function normalizeTab(value: string | null): LegalDocumentKey {
    return LEGAL_KEYS.includes(value as LegalDocumentKey) ? (value as LegalDocumentKey) : "terms";
}

function AuthRouteContent() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const redirectTo = searchParams.get("redirect") ?? undefined;
    const route = searchParams.get("routes");
    const legalOpen = route === "legal";
    const activeTab = normalizeTab(searchParams.get("tab"));
    const isClient = useIsClient();

    const updateLegalTab = (tab: LegalDocumentKey) => {
        const params = new URLSearchParams(searchParams.toString());
        params.set("routes", "legal");
        params.set("tab", tab);
        router.replace(`/sign-in?${params.toString()}`);
    };

    const closeLegal = () => {
        const params = new URLSearchParams(searchParams.toString());
        params.delete("routes");
        params.delete("tab");
        const query = params.toString();
        router.replace(query ? `/sign-in?${query}` : "/sign-in");
    };

    if (route === "forgot-password") {
        return <ForgotPasswordView />;
    }

    return (
        <>
            <SignInView redirectTo={redirectTo} />
            {isClient ? (
                <Modal centered destroyOnHidden footer={null} onCancel={closeLegal} open={legalOpen} width={800}>
                    <LegalView activeTab={activeTab} onTabChange={updateLegalTab} />
                </Modal>
            ) : null}
        </>
    );
}

export function AuthRoute() {
    return (
        <Suspense
            fallback={
                <Flex justify="center" className="py-20">
                    <Spin />
                </Flex>
            }
        >
            <AuthRouteContent />
        </Suspense>
    );
}
