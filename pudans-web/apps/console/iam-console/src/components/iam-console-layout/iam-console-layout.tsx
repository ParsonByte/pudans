"use client";

import { useState, type ReactNode } from "react";

import { AppHeader } from "@pudans-dev/share-component";
import { ConfigProvider, Layout } from "antd";
import zhCN from "antd/locale/zh_CN";

import { IamConsoleSidebar } from "../iam-console-sidebar/iam-console-sidebar";

import "./iam-console-layout.modules.less";

const { Content, Footer } = Layout;

const authUrl = process.env.NEXT_PUBLIC_AUTH_URL;
const homeUrl = process.env.NEXT_PUBLIC_HOME_URL;

type IamConsoleLayoutProps = {
  children: ReactNode;
};

export function IamConsoleLayout({ children }: IamConsoleLayoutProps) {
  const [collapsed, setCollapsed] = useState(false);
  const currentYear = new Date().getFullYear();

  return (
    <ConfigProvider locale={zhCN}>
      <div className="iam-console-layout">
        <div className="iam-console-layout__header">
          <AppHeader
            brandHref={homeUrl}
            consoleHref="/"
            loginHref={authUrl}
            menus={[]}
          />
        </div>
        <IamConsoleSidebar collapsed={collapsed} onCollapse={setCollapsed} />
        <div
          className="iam-console-layout__body"
          style={{ marginLeft: collapsed ? 0 : 200 }}
        >
          <Layout className="iam-console-layout__content">
            <Content style={{ margin: "24px 16px 0" }}>{children}</Content>
            <Footer style={{ textAlign: "center" }}>PUDANS ©{currentYear}</Footer>
          </Layout>
        </div>
      </div>
    </ConfigProvider>
  );
}