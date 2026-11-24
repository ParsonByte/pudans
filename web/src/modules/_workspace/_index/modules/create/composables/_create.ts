export const CREATE_SECTIONS = {
    activities: "activities",
    discover: "discover",
    templates: "templates",
} as const;

export type CreateSection = (typeof CREATE_SECTIONS)[keyof typeof CREATE_SECTIONS];

const CREATE_SECTION_VALUES: readonly string[] = Object.values(CREATE_SECTIONS);

export const SECTION_FILTERS: Record<CreateSection, readonly string[]> = {
    [CREATE_SECTIONS.discover]: [
        "全部",
        "热门趋势",
        "CG 动画",
        "视觉大片",
        "创意脑洞",
        "电影叙事",
        "种草带货",
        "视觉艺术",
    ],
    [CREATE_SECTIONS.templates]: [
        "热门",
        "最新",
        "心动加载",
        "AI 热舞",
        "社交媒体",
        "焕颜一新",
        "整活脑洞",
        "节日氛围",
        "电商广告",
        "萌宠",
        "风格化",
        "变身",
        "超现实",
    ],
    [CREATE_SECTIONS.activities]: [],
};

export function isCreateSection(value: string): value is CreateSection {
    return CREATE_SECTION_VALUES.includes(value);
}
