import type { Metadata } from "next";

import { AntdRegistry } from "@ant-design/nextjs-registry";

import "./global.less";

export const metadata: Metadata = {
  title: "登录 · PUDANS",
  description: "企业身份与权限管理平台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>{children}</AntdRegistry>
      </body>
    </html>
  );
}