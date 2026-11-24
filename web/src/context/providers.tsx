"use client";

import { type ReactNode, useEffect, useState } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ThemeProvider, useTheme } from "next-themes";
import { ConfigProvider, MessagePlugin, NotificationPlugin } from "tdesign-react";
import "tdesign-react/es/_util/react-19-adapter";

function PromptPluginConfig() {
    useEffect(() => {
        MessagePlugin.config({ attach: "body", placement: "top" });
        NotificationPlugin.config({ attach: "body", placement: "top-right" });
    }, []);

    return null;
}

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
            <PromptPluginConfig />
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
