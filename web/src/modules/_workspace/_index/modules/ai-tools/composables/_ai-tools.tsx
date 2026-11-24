import type { ReactNode } from "react";

import {
    AiCutIcon,
    AiEditIcon,
    AiToolIcon,
    AiVideoIcon,
    AnimationIcon,
    ImageSearchIcon,
    TextStyleIcon,
    ToolsIcon,
    UserTalkIcon,
    VideoCamera2Icon,
    VideoLibraryIcon,
} from "tdesign-icons-react";

import type { AiToolCoverTone } from "../components/ai-tool-card";

export const AI_TOOL_CATEGORIES = ["全部", "精选", "视频", "图片", "营销与商品", "镜头与动态"] as const;

export type AiToolCategory = (typeof AI_TOOL_CATEGORIES)[number];

type AiToolGroupKey = "featured" | "video";

type AiTool = {
    coverTone: AiToolCoverTone;
    description: string;
    icon: ReactNode;
    id: string;
    title: string;
};

type AiToolGroup = {
    key: AiToolGroupKey;
    title: string;
    tools: readonly AiTool[];
};

export const AI_TOOL_GROUPS: readonly AiToolGroup[] = [
    {
        key: "featured",
        title: "精选",
        tools: [
            {
                coverTone: "clay",
                description: "爆款视频，全新演绎",
                icon: <VideoLibraryIcon size={28} />,
                id: "video-replicate",
                title: "视频复刻",
            },
            {
                coverTone: "steel",
                description: "标记画面，精准修改",
                icon: <AiEditIcon size={28} />,
                id: "smart-edit",
                title: "智能编辑",
            },
            {
                coverTone: "sky",
                description: "提升画质，最高 8K",
                icon: <ImageSearchIcon size={28} />,
                id: "smart-upscale",
                title: "智能超清",
            },
            {
                coverTone: "sand",
                description: "重新生成指定片段",
                icon: <AiCutIcon size={28} />,
                id: "local-retake",
                title: "视频局部重拍",
            },
            {
                coverTone: "azure",
                description: "解析画面与镜头语言",
                icon: <TextStyleIcon size={28} />,
                id: "prompt-reverse",
                title: "提示词反推",
            },
            {
                coverTone: "ocean",
                description: "在你喜欢的 Agent 与终端中使用 DANS",
                icon: <ToolsIcon size={28} />,
                id: "dans-plugin-cli",
                title: "DANS 插件与 CLI",
            },
        ],
    },
    {
        key: "video",
        title: "视频",
        tools: [
            {
                coverTone: "slate",
                description: "多模态参考，生成完整视频",
                icon: <AiVideoIcon size={28} />,
                id: "video-generation",
                title: "视频生成",
            },
            {
                coverTone: "mint",
                description: "按时间线剪辑，局部精修",
                icon: <VideoCamera2Icon size={28} />,
                id: "video-edit",
                title: "视频编辑",
            },
            {
                coverTone: "peach",
                description: "声音口型自然同步",
                icon: <UserTalkIcon size={28} />,
                id: "digital-human",
                title: "数字人",
            },
            {
                coverTone: "forest",
                description: "自然衔接后续镜头",
                icon: <AnimationIcon size={28} />,
                id: "video-extension",
                title: "视频延长",
            },
            {
                coverTone: "rose",
                description: "重构画面，智能补全",
                icon: <AiToolIcon size={28} />,
                id: "video-interpolation",
                title: "视频补帧",
            },
        ],
    },
];

export const CATEGORY_GROUPS: Record<AiToolCategory, readonly AiToolGroupKey[]> = {
    全部: ["featured", "video"],
    精选: ["featured"],
    视频: ["video"],
    图片: [],
    营销与商品: [],
    镜头与动态: ["video"],
};
