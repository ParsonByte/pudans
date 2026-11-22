import type { Metadata } from "next";

import "@fontsource-variable/jetbrains-mono";
import "@fontsource-variable/noto-sans";

import { Providers } from "@/context/providers";
import "@/styles/_global.css";

export const metadata: Metadata = {
    title: "Pudans 控制台",
    description: "企业身份与权限管理平台",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
    return (
        <html lang="zh-CN" suppressHydrationWarning>
            <body>
                <Providers>{children}</Providers>
            </body>
        </html>
    );
}
