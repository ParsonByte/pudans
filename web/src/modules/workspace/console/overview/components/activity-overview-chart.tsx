import type { LoginTrendPoint } from "@pudans-dev/api-client";

import { ActivityBarChart } from "@/components/activity-bar-chart";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

type ActivityOverviewChartProps = {
  points: LoginTrendPoint[];
};

export function ActivityOverviewChart({ points }: ActivityOverviewChartProps) {
  const hasData = points.some((point) => point.date);

  return (
    <ConsoleSection>
      <ConsoleSectionHeader title="活动概览" description="最近 7 天操作次数" />
      {hasData ? <ActivityBarChart points={points} /> : <ConsoleEmpty>暂无活动数据。</ConsoleEmpty>}
    </ConsoleSection>
  );
}
