"use client";

import { Card, Empty } from "antd";

import "./page-placeholder.modules.less";

type PagePlaceholderProps = {
  description?: string;
};

export function PagePlaceholder({ description = "暂无数据" }: PagePlaceholderProps) {
  return (
    <Card>
      <div className="page-placeholder">
        <Empty description={description} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </div>
    </Card>
  );
}