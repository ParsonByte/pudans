"use client";

import { Descriptions, Space, Tag } from "tdesign-react";

const LABELS: Record<string, string> = {
    action: "动作",
    actionSnapshot: "动作快照",
    annotations: "注解",
    apiVersion: "API 版本",
    archived: "已归档",
    blockSeconds: "阻断时长（秒）",
    bucketName: "存储桶",
    component: "组件",
    context: "上下文",
    description: "描述",
    displayName: "显示名称",
    enabled: "启用",
    display: "显示",
    durationMs: "耗时（毫秒）",
    failureCount: "失败次数",
    finalizers: "终结器",
    fingerprint: "客户端指纹",
    formSchema: "表单字段",
    group: "分组",
    icon: "图标",
    ip: "来源 IP",
    key: "键",
    kind: "类型",
    labels: "标签",
    lastDeliveredAt: "最近投递时间",
    lastDeliveryStatus: "最近投递状态",
    maxAttempts: "最大尝试次数",
    message: "消息",
    metadata: "元数据",
    metric: "指标",
    name: "名称",
    operation: "操作",
    operations: "操作",
    originalName: "原始文件名",
    owner: "所有者",
    parentAuditId: "父审计编号",
    parent: "父级",
    parentIcon: "父级图标",
    path: "路径",
    policy: "存储策略",
    ready: "就绪",
    reason: "原因",
    rememberMeSupport: "记住登录",
    roleRefs: "角色引用",
    rules: "权限规则",
    requestMethod: "请求方法",
    requestPath: "请求路径",
    resourceName: "资源名称",
    resourceType: "资源类型",
    result: "结果",
    scene: "场景",
    secret: "密钥",
    size: "大小",
    spec: "配置",
    state: "状态",
    status: "状态",
    stepIndex: "步骤序号",
    syntheticFlow: "合成链路",
    targetRef: "目标类型",
    threshold: "阈值",
    timestamp: "时间",
    timeoutMillis: "超时（毫秒）",
    traceFlowId: "追踪链路编号",
    traceId: "追踪编号",
    type: "类型",
    uid: "用户编号",
    url: "地址",
    userAgent: "客户端",
    username: "用户名",
    windowSeconds: "窗口（秒）",
};

function labelOf(key: string): string {
    return LABELS[key] ?? key;
}

function renderValue(value: unknown): React.ReactNode {
    if (value === null || value === undefined || value === "") {
        return "—";
    }

    if (typeof value === "boolean") {
        return value ? "是" : "否";
    }

    if (typeof value === "string" || typeof value === "number") {
        return String(value);
    }

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return "—";
        }

        if (value.every((item) => typeof item !== "object" || item === null)) {
            return (
                <Space breakLine size={[4, 4]}>
                    {value.map((item, index) => (
                        <Tag key={`${String(item)}-${index}`}>{String(item)}</Tag>
                    ))}
                </Space>
            );
        }

        return (
            <div className="flex flex-col gap-2">
                {value.map((item, index) => (
                    <ObjectDetails key={index} value={item} />
                ))}
            </div>
        );
    }

    if (typeof value === "object") {
        return <ObjectDetails value={value} />;
    }

    return String(value);
}

type ObjectDetailsProps = {
    value: unknown;
};

export function ObjectDetails({ value }: ObjectDetailsProps) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
        return <span>—</span>;
    }

    const entries = Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== null);

    if (entries.length === 0) {
        return <span>—</span>;
    }

    return (
        <Descriptions
            bordered
            className="object-details"
            column={1}
            items={entries.map(([key, entry]) => ({
                content: renderValue(entry),
                label: labelOf(key),
            }))}
            size="small"
        />
    );
}
