"use client";

import { type ReactNode, Suspense } from "react";

import { Loading } from "tdesign-react";

type AppContainerProps = {
    children: ReactNode;
};

export function AppContainer({ children }: AppContainerProps) {
    return (
        <main className="app-page">
            <Suspense
                fallback={
                    <div className="flex min-h-56 items-center justify-center">
                        <Loading size="small" />
                    </div>
                }
            >
                {children}
            </Suspense>
        </main>
    );
}
