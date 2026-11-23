"use client";

import { Suspense } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import { Loading } from "tdesign-react";

import { ForgotPasswordView, LEGAL_KEYS, LegalDialog, type LegalDocumentKey, SignInView } from "@/modules/_auth";

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
            <LegalDialog activeTab={activeTab} onClose={closeLegal} onTabChange={updateLegalTab} open={legalOpen} />
        </>
    );
}

export function AuthRoute() {
    return (
        <Suspense
            fallback={
                <div className="flex justify-center py-20">
                    <Loading loading />
                </div>
            }
        >
            <AuthRouteContent />
        </Suspense>
    );
}
