import type { Metadata } from "next";

import { AntdRegistry } from "@ant-design/nextjs-registry";

import { IamConsoleLayout } from "@/components/iam-console-layout/iam-console-layout";

import "./global.less";

export const metadata: Metadata = {
  title: "IAM 访问控制 · PUDANS",
  description: "PUDANS IAM 访问控制台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <IamConsoleLayout>{children}</IamConsoleLayout>
        </AntdRegistry>
      </body>
    </html>
  );
}