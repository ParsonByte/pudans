"use client";

import type { ReactNode } from "react";

import { Card } from "antd";

type PanelCardProps = {
  children?: ReactNode;
  extra?: ReactNode;
  title: ReactNode;
};

export function PanelCard({ children, extra, title }: PanelCardProps) {
  return (
    <Card extra={extra} title={title}>
      {children}
    </Card>
  );
}