"use client";

import type { CSSProperties, ReactNode } from "react";

import { theme } from "antd";

import { AppHeader, type AppHeaderProps } from "../../components/app-header";

import "./app-layout.modules.less";

export type AppLayoutProps = {
  children: ReactNode;
  className?: string;
  contentClassName?: string;
  contentStyle?: CSSProperties;
  headerProps?: Omit<AppHeaderProps, "className">;
  style?: CSSProperties;
};

export function AppLayout({
  children,
  className,
  contentClassName,
  contentStyle,
  headerProps,
  style,
}: AppLayoutProps) {
  const { token } = theme.useToken();
  const rootStyle = {
    ...style,
    "--pudans-layout-bg": token.colorBgLayout,
    "--pudans-layout-scrollbar": token.colorTextTertiary,
    "--pudans-layout-scrollbar-hover": token.colorTextSecondary,
    "--pudans-layout-scrollbar-radius": `${token.borderRadiusSM}px`,
  } as CSSProperties;

  return (
    <div
      className={["pudans-app-layout", className].filter(Boolean).join(" ")}
      style={rootStyle}
    >
      <div className="pudans-app-layout__header">
        <AppHeader {...headerProps} />
      </div>
      <main
        className={["pudans-app-layout__content", contentClassName]
          .filter(Boolean)
          .join(" ")}
        style={contentStyle}
      >
        {children}
      </main>
    </div>
  );
}