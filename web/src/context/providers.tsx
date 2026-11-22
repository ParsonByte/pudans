"use client";

import { type ReactNode, useState } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { App as AntdApp, ConfigProvider, theme } from "antd";
import zhCN from "antd/locale/zh_CN";
import { ThemeProvider, useTheme } from "next-themes";

function AntdTheme({ children }: { children: ReactNode }) {
    const { resolvedTheme } = useTheme();

    return (
        <ConfigProvider
            locale={zhCN}
            theme={{
                algorithm: resolvedTheme === "dark" ? theme.darkAlgorithm : theme.defaultAlgorithm,
                token: {
                    fontFamily: "var(--font-sans)",
                },
            }}
        >
            <AntdApp>{children}</AntdApp>
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
            <AntdTheme>
                <QueryClientProvider client={queryClient}>
                    {children}
                    {process.env.NODE_ENV === "development" ? <ReactQueryDevtools initialIsOpen={false} /> : null}
                </QueryClientProvider>
            </AntdTheme>
        </ThemeProvider>
    );
}
