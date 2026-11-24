import type { ReactNode } from "react";

import {
    ChatBubbleIcon,
    CompassIcon,
    DataBaseIcon,
    FocusIcon,
    ImageIcon,
    LayersIcon,
    UsercaseIcon,
} from "tdesign-icons-react";

export type UnderstandingDimension = {
    description: string;
    icon: ReactNode;
    id: string;
    label: string;
    sources: readonly string[];
    x: number;
    y: number;
};

export const UNDERSTANDING_DIMENSIONS = [
    {
        description: "识别你真正想解决的问题、期望结果与隐含约束。",
        icon: <CompassIcon size={17} />,
        id: "intent",
        label: "意图",
        sources: ["对话目标", "指令约束", "工作上下文"],
        x: 50,
        y: 8,
    },
    {
        description: "理解你在风格、节奏、表达与创作选择上的稳定倾向。",
        icon: <FocusIcon size={17} />,
        id: "preference",
        label: "偏好",
        sources: ["创作选择", "编辑与修正", "显式反馈"],
        x: 78,
        y: 23,
    },
    {
        description: "记住已经确认的事实，并把它们与当前任务准确关联。",
        icon: <DataBaseIcon size={17} />,
        id: "fact",
        label: "事实",
        sources: ["已确认资料", "长期记忆", "项目记录"],
        x: 86,
        y: 58,
    },
    {
        description: "串联跨会话的项目背景、阶段目标与已有产出。",
        icon: <LayersIcon size={17} />,
        id: "project",
        label: "项目",
        sources: ["项目设定", "资产与主体", "历史产出"],
        x: 68,
        y: 86,
    },
    {
        description: "识别角色、商品、场景与风格主体，保持跨作品一致。",
        icon: <UsercaseIcon size={17} />,
        id: "subject",
        label: "主体",
        sources: ["角色设定", "商品资料", "场景与风格"],
        x: 32,
        y: 86,
    },
    {
        description: "从历史对话中提取目标、上下文和你做过的修正。",
        icon: <ChatBubbleIcon size={17} />,
        id: "conversation",
        label: "对话",
        sources: ["历史会话", "上下文引用", "人工修正"],
        x: 14,
        y: 58,
    },
    {
        description: "理解作品迭代中的取舍，让下一次创作更接近你的判断。",
        icon: <ImageIcon size={17} />,
        id: "creation",
        label: "创作",
        sources: ["作品版本", "生成参数", "反馈记录"],
        x: 22,
        y: 23,
    },
] as const satisfies readonly UnderstandingDimension[];

export type UnderstandingDimensionId = (typeof UNDERSTANDING_DIMENSIONS)[number]["id"];
