"use client";

import type { ReactNode } from "react";

import { Card, Skeleton, theme } from "antd";

import "./statistic-card.modules.less";

type StatisticCardProps = {
  icon: ReactNode;
  loading?: boolean;
  title: string;
  value?: number | string | null;
};

export function StatisticCard({ icon, loading = false, title, value }: StatisticCardProps) {
  const { token } = theme.useToken();

  return (
    <Card className="statistic-card">
      {loading ? (
        <Skeleton active paragraph={{ rows: 1 }} title={false} />
      ) : (
        <div className="statistic-card__inner">
          <span
            className="statistic-card__icon"
            style={{ backgroundColor: token.colorPrimaryBg, color: token.colorPrimary }}
          >
            {icon}
          </span>
          <div className="statistic-card__content">
            <div className="statistic-card__title">{title}</div>
            <div className="statistic-card__value">{value ?? "-"}</div>
          </div>
        </div>
      )}
    </Card>
  );
}