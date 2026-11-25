"use client";

import { Descriptions, Space, Tag } from "tdesign-react";

import { CopyableIdentifier } from "./copyable-identifier";

// 审计记录中的标识字段沿用长标识脱敏显示与复制入口。
export const AUDIT_IDENTIFIER_KEYS = ["name", "parentAuditId", "traceFlowId", "traceId", "uid"];

const LABELS: Record<string, string> = {
    action: "动作",
    actionSnapshot: "动作快照",
    anchorAvatar: "主播头像",
    anchorId: "主播 ID",
    anchorName: "主播",
    annotations: "注解",
    apiVersion: "API 版本",
    archived: "已归档",
    attempts: "尝试次数",
    auto: "自动录制",
    autoRecord: "自动录制",
    blockSeconds: "阻断时长（秒）",
    bucketName: "存储桶",
    checkIntervalSeconds: "检查间隔（秒）",
    cleanupAttempts: "清理尝试次数",
    component: "组件",
    context: "上下文",
    cooldownUntil: "冷却截止",
    currentRecording: "当前录制",
    description: "描述",
    display: "显示",
    displayName: "显示名称",
    durationMillis: "时长（毫秒）",
    durationMs: "耗时（毫秒）",
    enabled: "启用",
    endedAt: "结束时间",
    errorCode: "错误码",
    events: "订阅事件",
    failureCount: "失败次数",
    filename: "文件名",
    finalizers: "终结器",
    fingerprint: "客户端指纹",
    formSchema: "表单字段",
    format: "封装格式",
    group: "分组",
    heartbeatAt: "心跳时间",
    icon: "图标",
    index: "分段序号",
    ip: "来源 IP",
    key: "键",
    kind: "类型",
    labels: "标签",
    lastDeliveredAt: "最近投递时间",
    lastDeliveryStatus: "最近投递状态",
    lastLiveAt: "最近开播",
    lastOfflineAt: "最近下播",
    lastPacketAt: "最近数据包",
    lastResolveCode: "解析结果码",
    lastResolveError: "解析错误",
    lastResolvedAt: "最近解析",
    lastStreamAt: "最近拉流",
    lastWriteAt: "最近写入",
    live: "正在直播",
    liveStatus: "直播状态",
    localBytes: "本地字节数",
    maxAttempts: "最大尝试次数",
    mergeIntervalSeconds: "合并间隔（秒）",
    message: "消息",
    metadata: "元数据",
    metric: "指标",
    name: "名称",
    nextCheckAt: "下次检查",
    operation: "操作",
    operations: "操作",
    originalName: "原始文件名",
    owner: "所有者",
    ownerInstance: "录制实例",
    parent: "父级",
    parentAuditId: "父审计编号",
    parentIcon: "父级图标",
    partCount: "分段数量",
    parts: "分段",
    path: "路径",
    platform: "平台",
    policy: "存储策略",
    rateLimited: "已限流",
    ready: "就绪",
    reason: "原因",
    recordingFormat: "录制格式",
    rememberMeSupport: "记住登录",
    requestMethod: "请求方法",
    requestPath: "请求路径",
    requestedBy: "发起人",
    resolveFailures: "解析失败次数",
    resourceName: "资源名称",
    resourceType: "资源类型",
    result: "结果",
    retryOf: "重试自",
    roleRefs: "角色引用",
    roomId: "房间 ID",
    roomTitle: "直播间标题",
    roomUrl: "直播间链接",
    rules: "权限规则",
    scene: "场景",
    secret: "密钥",
    segmentCount: "切片数量",
    segmentSeconds: "分段时长（秒）",
    size: "大小",
    sourceName: "直播源",
    spec: "配置",
    startedAt: "开始时间",
    state: "状态",
    status: "状态",
    stepIndex: "步骤序号",
    stopRequestedAt: "停止请求时间",
    storageFile: "存储文件",
    storagePolicy: "存储策略",
    syntheticFlow: "合成链路",
    targetRef: "目标类型",
    threshold: "阈值",
    timestamp: "时间",
    timeoutMillis: "超时（毫秒）",
    totalBytes: "总字节数",
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

function renderValue(key: string, value: unknown, emptyText: string, maskKeys?: string[]): React.ReactNode {
    if (value === null || value === undefined || value === "") {
        return emptyText;
    }

    if (typeof value === "boolean") {
        return value ? "是" : "否";
    }

    if (typeof value === "string" && maskKeys?.includes(key)) {
        return <CopyableIdentifier mask value={value} />;
    }

    if (typeof value === "string" || typeof value === "number") {
        return String(value);
    }

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return emptyText;
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
                    <ObjectDetails emptyText={emptyText} key={index} maskKeys={maskKeys} value={item} />
                ))}
            </div>
        );
    }

    if (typeof value === "object") {
        return <ObjectDetails emptyText={emptyText} maskKeys={maskKeys} value={value} />;
    }

    return String(value);
}

type ObjectDetailsProps = {
    emptyText?: string;
    maskKeys?: string[];
    value: unknown;
};

export function ObjectDetails({ emptyText = "—", maskKeys, value }: ObjectDetailsProps) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
        return <span>{emptyText}</span>;
    }

    const entries = Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== null);

    if (entries.length === 0) {
        return <span>{emptyText}</span>;
    }

    return (
        <Descriptions
            bordered
            className="object-details"
            column={1}
            items={entries.map(([key, entry]) => ({
                content: renderValue(key, entry, emptyText, maskKeys),
                label: labelOf(key),
            }))}
            size="small"
        />
    );
}
