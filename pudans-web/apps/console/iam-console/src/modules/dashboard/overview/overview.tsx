"use client";

import {
  AppstoreOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import { Col, Empty, Row } from "antd";

import { PanelCard } from "@/components/panel-card/panel-card";
import { StatisticCard } from "@/components/statistic-card/statistic-card";
import { TimeRange } from "@/components/time-range/time-range";

import "./overview.modules.less";

const statisticCol = { xs: 24, sm: 12, lg: 12, xl: 6, style: { marginBottom: 24 } };
const panelCol = { xs: 24, sm: 24, md: 24, lg: 12, xl: 12, style: { marginBottom: 24 } };

function ChartPlaceholder() {
  return (
    <div className="overview-chart">
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
    </div>
  );
}

export function Overview() {
  return (
    <div className="overview">
      <Row gutter={[24, 24]}>
        <Col {...statisticCol}>
          <StatisticCard icon={<SafetyCertificateOutlined />} title="今日认证量" />
        </Col>
        <Col {...statisticCol}>
          <StatisticCard icon={<RocketOutlined />} title="认证源总数" />
        </Col>
        <Col {...statisticCol}>
          <StatisticCard icon={<TeamOutlined />} title="用户数量" />
        </Col>
        <Col {...statisticCol}>
          <StatisticCard icon={<AppstoreOutlined />} title="应用数量" />
        </Col>
      </Row>
      <Row gutter={[24, 24]}>
        <Col {...panelCol}>
          <PanelCard extra={<TimeRange />} title="认证量">
            <ChartPlaceholder />
          </PanelCard>
        </Col>
        <Col {...panelCol}>
          <PanelCard extra={<TimeRange />} title="应用访问排名（前十）">
            <ChartPlaceholder />
          </PanelCard>
        </Col>
      </Row>
      <Row gutter={[24, 24]}>
        <Col {...panelCol}>
          <PanelCard extra={<TimeRange />} title="热门认证方式">
            <ChartPlaceholder />
          </PanelCard>
        </Col>
        <Col {...panelCol}>
          <PanelCard extra={<TimeRange />} title="登录位置分布">
            <ChartPlaceholder />
          </PanelCard>
        </Col>
      </Row>
    </div>
  );
}