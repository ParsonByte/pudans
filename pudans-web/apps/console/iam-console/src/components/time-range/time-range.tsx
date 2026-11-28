"use client";

import { useState } from "react";

import { Segmented } from "antd";

export type TimeRangeValue = "today" | "week" | "month" | "year";

type TimeRangeProps = {
  defaultValue?: TimeRangeValue;
  onChange?: (value: TimeRangeValue) => void;
};

const options = [
  { label: "今日", value: "today" },
  { label: "本周", value: "week" },
  { label: "本月", value: "month" },
  { label: "全年", value: "year" },
];

export function TimeRange({ defaultValue = "today", onChange }: TimeRangeProps) {
  const [value, setValue] = useState<TimeRangeValue>(defaultValue);

  return (
    <Segmented
      onChange={(next) => {
        setValue(next as TimeRangeValue);
        onChange?.(next as TimeRangeValue);
      }}
      options={options}
      value={value}
    />
  );
}