"use client";

import { type ReactNode, useState } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ThemeProvider, useTheme } from "next-themes";
import { ConfigProvider } from "tdesign-react";

function TDesignTheme({ children }: { children: ReactNode }) {
    const { resolvedTheme } = useTheme();

    return (
        <ConfigProvider
            globalConfig={{
                animation: {
                    include: resolvedTheme === "dark" ? ["expand", "fade"] : ["ripple", "expand", "fade"],
                },
                attach: {
                    dialog: "body",
                    popup: "body",
                },
                dialog: {
                    closeOnEscKeydown: true,
                    closeOnOverlayClick: false,
                    confirmBtnTheme: {
                        danger: "danger",
                        default: "primary",
                        info: "primary",
                        success: "success",
                        warning: "warning",
                    },
                    placement: "center",
                },
                isContextEffectPlugin: true,
            }}
        >
            {children}
        </ConfigProvider>
    );
}

export function Providers({ children }: { children: ReactNode }) {
    const [queryClient] = useState(
        () =>
            new QueryClient({
                defaultOptions: {
                    queries: {
                        staleTime: 60_000,
                        refetchOnWindowFocus: false,
                    },
                },
            }),
    );

    return (
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
            <TDesignTheme>
                <QueryClientProvider client={queryClient}>
                    {children}
                    {process.env.NODE_ENV === "development" ? <ReactQueryDevtools initialIsOpen={false} /> : null}
                </QueryClientProvider>
            </TDesignTheme>
        </ThemeProvider>
    );
}
