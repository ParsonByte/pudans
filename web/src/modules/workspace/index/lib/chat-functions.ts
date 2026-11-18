import { CircleCheck, Frame, Gem, Lightbulb, PenLine, ScanSearch, Zap } from "lucide-react";

export const CHAT_FUNCTIONS = [
  { label: "深度解析", icon: ScanSearch },
  { label: "贡献灵感", icon: PenLine },
  { label: "任务规划", icon: CircleCheck },
  { label: "灵感库", icon: Lightbulb },
  { label: "无限画布", icon: Frame },
] as const;

export const CHAT_MODES = [
  { value: "fast", label: "快速", icon: Zap },
  { value: "pro", label: "进阶", icon: Gem },
] as const;

export type ChatFunction = (typeof CHAT_FUNCTIONS)[number];

export type ChatMode = (typeof CHAT_MODES)[number]["value"];
