import type { Metadata } from "next";

import { AntdRegistry } from "@ant-design/nextjs-registry";

import "./global.less";

export const metadata: Metadata = {
  title: "开始创作 · PUDANS",
  description: "PUDANS —— 企业内部一站式管理平台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <head>
        <link
          href="https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;700;900&family=Space+Grotesk:wght@500;700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <AntdRegistry>{children}</AntdRegistry>
      </body>
    </html>
  );
}