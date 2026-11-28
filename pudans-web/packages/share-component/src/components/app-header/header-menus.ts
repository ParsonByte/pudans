import type { AppHeaderMenu } from "./header-types";

export const DEFAULT_APP_HEADER_MENUS: AppHeaderMenu[] = [
  {
    key: "products",
    label: "产品",
    description: "从身份、权限到数据与消息，一套平台承载企业核心基础设施。",
    href: "/products",
    groups: [
      {
        key: "identity",
        title: "身份与权限",
        items: [
          {
            key: "iam",
            label: "身份与访问管理",
            description: "登录认证、MFA、单点登录与身份源同步",
            href: "/products/iam",
          },
          {
            key: "authorization",
            label: "统一授权",
            description: "策略、角色与资源类型管理",
            href: "/products/authorization",
          },
          {
            key: "audit",
            label: "安全审计",
            description: "审计记录、登录追踪与访问复核",
            href: "/products/audit",
          },
        ],
      },
      {
        key: "data",
        title: "数据与内容",
        items: [
          {
            key: "repository",
            label: "文件仓储",
            description: "文件元数据与对象存储管理",
            href: "/products/repository",
          },
          {
            key: "search",
            label: "搜索服务",
            description: "索引、文档与统一检索",
            href: "/products/search",
          },
        ],
      },
      {
        key: "messaging",
        title: "消息与任务",
        items: [
          {
            key: "notification",
            label: "通知中心",
            description: "邮件、短信与协作平台通知",
            href: "/products/notification",
          },
          {
            key: "mq",
            label: "消息中心",
            description: "消息发布、投递与死信处理",
            href: "/products/mq",
          },
          {
            key: "job",
            label: "任务调度",
            description: "任务定义、执行记录与调度器",
            href: "/products/job",
          },
        ],
      },
    ],
  },
  {
    key: "applications",
    label: "应用",
    description: "面向企业与开发者的应用与工具集合。",
    href: "/apps",
    groups: [
      {
        key: "governance",
        title: "治理场景",
        items: [
          {
            key: "unified-identity",
            label: "统一身份",
            description: "员工、合作伙伴与客户身份统一管理",
            href: "/solutions/unified-identity",
          },
          {
            key: "permission-governance",
            label: "权限治理",
            description: "最小权限、职责分离与定期复核",
            href: "/solutions/permission-governance",
          },
          {
            key: "security-compliance",
            label: "安全合规",
            description: "审计留痕与访问风险控制",
            href: "/solutions/security-compliance",
          },
        ],
      },
      {
        key: "platform",
        title: "平台建设",
        items: [
          {
            key: "multi-tenant",
            label: "多租户平台",
            description: "面向 SaaS 与内部平台的租户隔离",
            href: "/solutions/multi-tenant",
          },
          {
            key: "legacy-integration",
            label: "存量系统集成",
            description: "统一接入既有身份与权限体系",
            href: "/solutions/legacy-integration",
          },
        ],
      },
    ],
  },
  {
    key: "services",
    label: "服务",
    description: "从接入、部署到运维的完整服务支持。",
    href: "/services",
    groups: [
      {
        key: "develop",
        title: "开发接入",
        items: [
          {
            key: "docs",
            label: "文档中心",
            description: "使用指南与最佳实践",
            href: "/docs",
          },
          {
            key: "api",
            label: "API 参考",
            description: "OpenAPI 与接口说明",
            href: "/docs/api",
          },
          {
            key: "sdk",
            label: "客户端 SDK",
            description: "前端与后端接入工具",
            href: "/docs/sdk",
          },
        ],
      },
      {
        key: "operations",
        title: "部署运维",
        items: [
          {
            key: "deployment",
            label: "部署指南",
            description: "本地与生产环境部署",
            href: "/docs/deployment",
          },
          {
            key: "operations-guide",
            label: "运维手册",
            description: "日常巡检、容量与故障处理",
            href: "/docs/operations",
          },
          {
            key: "observability",
            label: "可观测性",
            description: "指标、日志与链路追踪",
            href: "/docs/observability",
          },
        ],
      },
      {
        key: "support",
        title: "支持",
        items: [
          {
            key: "support-center",
            label: "技术支持",
            description: "问题反馈与技术支持",
            href: "/support",
          },
          {
            key: "faq",
            label: "常见问题",
            description: "高频问题与排查建议",
            href: "/faq",
          },
        ],
      },
    ],
  },
  {
    key: "about",
    label: "了解我们",
    description: "了解 PUDANS 的产品理念、团队与最新动态。",
    href: "/about",
    groups: [
      {
        key: "company",
        title: "公司",
        items: [
          {
            key: "about-us",
            label: "关于 PUDANS",
            description: "平台愿景与产品理念",
            href: "/about",
          },
          {
            key: "news",
            label: "产品动态",
            description: "版本发布与产品进展",
            href: "/about/news",
          },
          {
            key: "contact",
            label: "联系我们",
            description: "商务合作与技术支持",
            href: "/about/contact",
          },
        ],
      },
      {
        key: "resources",
        title: "资源",
        items: [
          {
            key: "help",
            label: "帮助中心",
            description: "快速查找使用帮助",
            href: "/docs",
          },
          {
            key: "terms",
            label: "服务条款",
            description: "平台服务与使用条款",
            href: "/legal/terms",
          },
          {
            key: "privacy",
            label: "隐私政策",
            description: "数据与隐私保护说明",
            href: "/legal/privacy",
          },
        ],
      },
    ],
  },
];
