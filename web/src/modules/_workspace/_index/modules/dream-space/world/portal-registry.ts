export type ExternalPortalRecord = {
    accent: string;
    description: string;
    host: string;
    id: string;
    label: string;
    url: string;
};

const EXTERNAL_PORTALS: readonly ExternalPortalRecord[] = [
    {
        accent: "#56D9FF",
        description: "Three.js 场景、材质与交互能力参考。",
        host: "threejs.org",
        id: "portal-threejs",
        label: "三维手稿馆",
        url: "https://threejs.org/docs/",
    },
    {
        accent: "#B49BFF",
        description: "React 并发渲染与客户端交互模式。",
        host: "react.dev",
        id: "portal-react",
        label: "组件炼金房",
        url: "https://react.dev/learn",
    },
    {
        accent: "#FFC857",
        description: "无障碍、浏览器能力与 Web 标准的共同记忆。",
        host: "developer.mozilla.org",
        id: "portal-mdn",
        label: "标准回音室",
        url: "https://developer.mozilla.org/zh-CN/docs/Web",
    },
    {
        accent: "#79E6B8",
        description: "检索视觉层次、色彩和版式线索。",
        host: "www.tdesign.team",
        id: "portal-tdesign",
        label: "界面灵感台",
        url: "https://www.tdesign.team/",
    },
];

const EXTERNAL_PORTAL_BY_ID = new Map(EXTERNAL_PORTALS.map((portal) => [portal.id, portal]));
const ALLOWED_HOSTS = new Set(EXTERNAL_PORTALS.map((portal) => portal.host));

export const portalRegistry = {
    get(id: string): ExternalPortalRecord | undefined {
        return EXTERNAL_PORTAL_BY_ID.get(id);
    },
    isAllowedHost(hostname: string): boolean {
        return ALLOWED_HOSTS.has(hostname);
    },
    list(): readonly ExternalPortalRecord[] {
        return EXTERNAL_PORTALS;
    },
};

export const EXTERNAL_PORTALS_LIST = EXTERNAL_PORTALS;
