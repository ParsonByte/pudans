"use client";

import type { CSSProperties } from "react";

import {
  AppstoreOutlined,
  BarChartOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PieChartOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import { Layout, Menu } from "antd";

import "./iam-console-sidebar.modules.less";

const { Sider } = Layout;

const siderStyle: CSSProperties = {
  position: "fixed",
  top: "var(--iam-console-header-height)",
  bottom: 0,
  left: 0,
  zIndex: 10,
};

const items: MenuProps["items"] = [
  {
    key: "/dashboard",
    icon: <PieChartOutlined />,
    label: "仪表盘",
  },
  {
    key: "/settings",
    icon: <SettingOutlined />,
    label: "设置",
  },
  {
    key: "/identity",
    icon: <TeamOutlined />,
    label: "身份管理",
    children: [
      { key: "/identity/users", label: "用户" },
      { key: "/identity/user-groups", label: "用户组" },
      { key: "/identity/roles", label: "角色" },
      { key: "/identity/identity-sources", label: "身份源" },
    ],
  },
  {
    key: "/permissions",
    icon: <SafetyCertificateOutlined />,
    label: "权限管理",
    children: [
      { key: "/permissions/authorization", label: "授权" },
      { key: "/permissions/policies", label: "权限策略" },
      { key: "/permissions/diagnosis", label: "权限诊断" },
    ],
  },
  {
    key: "/integration",
    icon: <AppstoreOutlined />,
    label: "集成管理",
    children: [
      { key: "/integration/identity-providers", label: "身份提供商" },
      { key: "/integration/apps", label: "应用列表" },
      { key: "/integration/apps/create", label: "创建应用" },
      { key: "/integration/app-groups", label: "应用分组" },
    ],
  },
  {
    key: "/analytics",
    icon: <BarChartOutlined />,
    label: "访问分析",
    children: [
      { key: "/analytics/analyzer", label: "分析器" },
      { key: "/analytics/structure", label: "分析结构" },
    ],
  },
];

type IamConsoleSidebarProps = {
  collapsed: boolean;
  onCollapse: (collapsed: boolean) => void;
};

export function IamConsoleSidebar({ collapsed, onCollapse }: IamConsoleSidebarProps) {
  return (
    <Sider
      collapsed={collapsed}
      collapsedWidth="0"
      collapsible
      onCollapse={onCollapse}
      style={siderStyle}
      theme="light"
      trigger={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
    >
      <div className="iam-console-sidebar__content">
        <div className="iam-console-sidebar__logo">IAM 访问控制</div>
        <Menu
          defaultSelectedKeys={["/dashboard"]}
          items={items}
          mode="inline"
          theme="light"
        />
      </div>
    </Sider>
  );
}