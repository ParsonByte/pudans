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
    icon: ReactNode;
    id: string;
    label: string;
    x: number;
    y: number;
};

export const UNDERSTANDING_DIMENSIONS = [
    {
        icon: <CompassIcon size={17} />,
        id: "intent",
        label: "意图",
        x: 50,
        y: 8,
    },
    {
        icon: <FocusIcon size={17} />,
        id: "preference",
        label: "偏好",
        x: 78,
        y: 23,
    },
    {
        icon: <DataBaseIcon size={17} />,
        id: "fact",
        label: "事实",
        x: 86,
        y: 58,
    },
    {
        icon: <LayersIcon size={17} />,
        id: "project",
        label: "项目",
        x: 68,
        y: 86,
    },
    {
        icon: <UsercaseIcon size={17} />,
        id: "subject",
        label: "主体",
        x: 32,
        y: 86,
    },
    {
        icon: <ChatBubbleIcon size={17} />,
        id: "conversation",
        label: "对话",
        x: 14,
        y: 58,
    },
    {
        icon: <ImageIcon size={17} />,
        id: "creation",
        label: "创作",
        x: 22,
        y: 23,
    },
] as const satisfies readonly UnderstandingDimension[];
