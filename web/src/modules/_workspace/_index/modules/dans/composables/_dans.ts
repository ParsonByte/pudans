export type Recommendation = {
    prompt: string;
    title?: string;
};

export const CHAT_RECOMMENDATIONS: readonly Recommendation[] = [
    { prompt: "资讯：大学生用 AI 造电子汴京城引 65 万人云摆摊" },
    { prompt: "帮我设计一个融合东方龙元素的西方奇幻风格恶龙形象" },
    { prompt: "给我一些脑洞大开的故事创意" },
    { prompt: "全国有多少个知名的吴家村？" },
];

export const WORK_RECOMMENDATIONS: readonly Recommendation[] = [
    { prompt: "把零散资料整理成一份云文档", title: "云文档" },
    { prompt: "做城市打卡攻略图", title: "创意设计" },
    { prompt: "上传服装平铺图，做模特换装走秀视频", title: "创意视频" },
    { prompt: "做一份新疆 7 天游攻略", title: "万能攻略" },
];
