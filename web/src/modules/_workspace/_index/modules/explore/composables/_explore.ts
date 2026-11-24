export const EXPLORE_CATEGORIES = [
    "短剧",
    "影视作品",
    "动画动漫",
    "广告营销",
    "平面设计",
    "电商",
    "其他",
    "技能",
    "活动",
] as const;

const DRAMA_WORK_TYPES = ["短剧作品", "漫剧作品"] as const;
const DEFAULT_WORK_TYPES = ["精选作品", "最新作品"] as const;
export const WORK_CARD_KEYS = Array.from({ length: 12 }, (_, index) => `explore-work-${index}`);

export type ExploreCategory = (typeof EXPLORE_CATEGORIES)[number];

export function exploreWorkTypes(category: ExploreCategory): readonly string[] {
    return category === "短剧" ? DRAMA_WORK_TYPES : DEFAULT_WORK_TYPES;
}
