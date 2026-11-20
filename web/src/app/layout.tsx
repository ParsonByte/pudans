import type { ReactNode } from "react";

import "@fontsource-variable/jetbrains-mono";
import "@fontsource-variable/noto-sans";
import type { Metadata } from "next";

import "@/styles/_global.css";

import { Providers } from "./providers";

export const metadata: Metadata = {
  title: "Pudans",
  icons: {
    icon: "/favicon.ico",
  },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning className="font-sans">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
