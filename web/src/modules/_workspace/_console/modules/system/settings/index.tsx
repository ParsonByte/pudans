"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useIsFetching, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { HelpCircleIcon, RefreshIcon } from "tdesign-icons-react";
import {
    Button,
    Col,
    Empty,
    Form,
    Input,
    InputNumber,
    RadioGroup,
    Row,
    Select,
    Skeleton,
    TagInput,
    Textarea,
    Tooltip,
} from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { ObjectDetails } from "../../../components/object-details";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { SettingLayout } from "../../../components/setting-layout";
import { SwitchField } from "../../../components/switch-field";
import { consoleErrorMessage } from "../../../composables/_errors";
import { IdentityDialogFooter } from "../../identity/components/identity-dialog-footer";
import { SiteImageField } from "./components/site-image-field";
import type { SiteAssetField } from "./composables/_site-assets";

const PARAMETER_SETTINGS_QUERY_ROOT = ["console", "parameter-settings"] as const;
const DEFAULT_PARAMETER_GROUP = "site";

const PARAMETER_GROUPS = [
    { key: "site", label: "站点" },
    { key: "session", label: "会话" },
    { key: "authentication", label: "认证" },
    { key: "captcha", label: "验证码" },
    { key: "mail", label: "邮件" },
    { key: "audit", label: "审计" },
    { key: "server", label: "服务" },
    { key: "storage", label: "存储" },
];

const FIELD_LABELS: Record<string, string> = {
    accessTokenTtlSeconds: "访问令牌有效期",
    alertChatId: "告警群 ID",
    announcementVersion: "公告版本",
    appId: "应用 ID",
    appSecret: "应用密钥",
    argon2PasswordStorageEnabled: "Argon2 密码存储",
    auditRecordRules: "审计记录规则",
    authentication: "认证",
    authorizationUri: "授权地址",
    avatarDimension: "头像尺寸",
    avatarMaxSize: "头像最大大小",
    avatarStoragePath: "头像存储路径",
    captchaInterferenceDots: "干扰点",
    captchaInterferenceText: "干扰字符",
    captchaLength: "验证码长度",
    captchaMode: "验证码模式",
    captchaProvider: "验证码提供方",
    captchaUseHollowLine: "空心线",
    captchaUseInterferenceText: "使用干扰字符",
    captchaUseNoise: "噪点",
    captchaUseSineLine: "正弦线",
    captchaUseWaveLine: "波浪线",
    defaultUserGroup: "默认用户组",
    downloadTokenTtlSeconds: "下载令牌有效期",
    exposeEmailToSignedInUsers: "向登录用户公开邮箱",
    favicon: "站点图标",
    feishu: "飞书登录",
    footerCode: "页脚代码",
    gcCron: "垃圾回收 cron",
    iconLarge: "大图标",
    iconMedium: "中图标",
    issuer: "签发方",
    loginCaptchaEnabled: "登录图片验证码",
    logoDark: "深色 Logo",
    logoLight: "浅色 Logo",
    mailFromAddress: "发件地址",
    mailFromName: "发件人名称",
    mailReplyTo: "回复地址",
    mailTemplates: "邮件模板",
    maxLifetimeDays: "最大有效期",
    mode: "记录模式",
    oauth2: "OAuth2 登录",
    oidc: "OIDC 登录",
    operations: "操作",
    passkeyLoginEnabled: "通行密钥登录",
    pat: "个人访问令牌",
    providers: "提供商",
    recoveryCaptchaEnabled: "找回密码图片验证码",
    redirectUri: "回调地址",
    refreshTokenTtlSeconds: "刷新令牌有效期",
    resourceTypes: "资源类型",
    rsaLoginEncryptionEnabled: "RSA 登录加密",
    scope: "授权范围",
    siteAnnouncement: "站点公告",
    siteDescription: "站点描述",
    siteId: "站点 ID",
    siteName: "站点名称",
    siteUrl: "站点地址",
    smtpConnectionTtlSeconds: "SMTP 连接有效期",
    smtpForceSsl: "强制 SSL",
    smtpHost: "SMTP 主机",
    smtpPort: "SMTP 端口",
    syncIntervalSeconds: "同步间隔",
    tempPath: "临时目录",
    totp: "TOTP 动态口令",
    trashRetentionDays: "回收站保留时长",
    uploadSessionTtlHours: "上传会话有效期",
    enabled: "启用",
};

const SECRET_FIELD_PATTERN = /password|secret|token|key/i;
const IMAGE_FIELDS = new Set(["favicon", "iconLarge", "iconMedium", "logoDark", "logoLight"]);
const LONG_TEXT_FIELDS = new Set(["footerCode", "siteAnnouncement", "siteDescription"]);
const HIDDEN_FIELDS = new Set(["announcementVersion"]);
const READ_ONLY_FIELDS = new Set(["siteId"]);
const OBJECT_ARRAY_FIELDS = new Set(["providers"]);
const FIELD_ROW_GUTTER: [number, number] = [32, 24];
const NUMBER_FIELD_MAX_WIDTH = 260;
const PROVIDER_FIELD_MAX_WIDTH = 360;
const SWITCH_FALLBACK_DESCRIPTION = "开启后生效。";
const SELECT_OPTIONS: Record<string, { label: string; value: string }[]> = {
    captchaMode: [
        { label: "字符", value: "TEXT" },
        { label: "算术", value: "MATH" },
    ],
    "auditRecordRules.mode": [
        { label: "白名单", value: "WHITELIST" },
        { label: "黑名单", value: "BLACKLIST" },
    ],
};
const CAPTCHA_PROVIDER_OPTIONS = [
    { label: "图片验证码", value: "image" },
    { label: "关闭验证", value: "none" },
];
const FIELD_HELP: Record<string, string> = {
    "auditRecordRules.mode": "白名单仅记录匹配项，黑名单跳过匹配项。",
    argon2PasswordStorageEnabled: "关闭后新设置的密码改用 BCrypt 存储。",
    avatarStoragePath: "相对于数据目录的头像存储目录，例如 avatars；头像由用户在个人资料中上传。",
    captchaInterferenceDots: "叠加更密集的干扰点。",
    captchaInterferenceText: "叠加更密集的干扰字符。",
    captchaMode: "字符模式显示随机文本，算术模式显示算式。",
    captchaProvider: "内置图片验证码为 image，none 表示关闭验证。",
    captchaUseHollowLine: "叠加空心线干扰。",
    captchaUseInterferenceText: "叠加少量干扰字符。",
    captchaUseNoise: "叠加噪点干扰。",
    captchaUseSineLine: "叠加正弦曲线干扰。",
    captchaUseWaveLine: "叠加波浪线干扰。",
    exposeEmailToSignedInUsers: "登录用户可查看其他用户的邮箱地址。",
    favicon: "支持 ICO、SVG、PNG 格式，最大 2 MB。",
    "feishu.enabled": "允许使用飞书账号登录。",
    gcCron: "使用六段式 cron 表达式。",
    iconLarge: "建议使用 512×512 的 PNG，最大 2 MB。",
    iconMedium: "建议使用 192×192 的 PNG，最大 2 MB。",
    loginCaptchaEnabled: "登录时要求完成图片验证码。",
    logoDark: "支持 PNG、JPG、WebP、SVG 格式，最大 2 MB。",
    logoLight: "支持 PNG、JPG、WebP、SVG 格式，最大 2 MB。",
    maxLifetimeDays: "0 表示不限制有效期。",
    "pat.enabled": "允许用户创建个人访问令牌。",
    passkeyLoginEnabled: "允许使用已绑定的硬件通行密钥登录。",
    recoveryCaptchaEnabled: "找回密码时要求完成图片验证码。",
    rsaLoginEncryptionEnabled: "提交前使用服务器 RSA 公钥加密登录密码。",
    siteId: "由系统在首次启动时自动生成。",
    smtpForceSsl: "SMTP 连接强制使用 SSL。",
    "totp.enabled": "登录时要求输入动态口令。",
    trashRetentionDays: "0 表示不自动清理。",
};
const NUMBER_UNITS: Record<string, string> = {
    accessTokenTtlSeconds: "秒",
    avatarDimension: "像素",
    avatarMaxSize: "字节",
    captchaLength: "位",
    downloadTokenTtlSeconds: "秒",
    maxLifetimeDays: "天",
    refreshTokenTtlSeconds: "秒",
    smtpConnectionTtlSeconds: "秒",
    syncIntervalSeconds: "秒",
    trashRetentionDays: "天",
    uploadSessionTtlHours: "小时",
};

type FieldColumns = {
    sm?: number;
    xs?: number;
};

type ParameterGroupLayout = {
    booleanFullWidth?: boolean;
    compactArrayFields?: ReadonlySet<string>;
    fieldColumns?: Record<string, FieldColumns>;
    labelAlign?: "left" | "top";
    labelWidth?: number;
    ownRowFields?: ReadonlySet<string>;
};

const PARAMETER_GROUP_LAYOUTS: Record<string, ParameterGroupLayout> = {
    authentication: {
        booleanFullWidth: true,
        compactArrayFields: new Set(["providers"]),
        fieldColumns: {
            "feishu.appId": { sm: 4, xs: 12 },
            "feishu.appSecret": { sm: 4, xs: 12 },
            "feishu.redirectUri": { sm: 4, xs: 12 },
        },
    },
    audit: {
        compactArrayFields: new Set(["operations", "resourceTypes"]),
        labelAlign: "left",
        labelWidth: 120,
        ownRowFields: new Set(["auditRecordRules.mode"]),
    },
};

function labelOf(key: string): string {
    return FIELD_LABELS[key] ?? key;
}

function renderFieldLabel(label: string, hint?: string): React.ReactNode {
    if (!hint) {
        return label;
    }

    return (
        <span className="inline-flex items-center gap-1">
            {label}
            <Tooltip content={hint} placement="top-left">
                <HelpCircleIcon />
            </Tooltip>
        </span>
    );
}

function isComplexArray(value: unknown): boolean {
    return Array.isArray(value) && value.some((item) => item !== null && typeof item === "object");
}

function isPrimitiveArray(value: unknown): boolean {
    return Array.isArray(value) && value.every((item) => item === null || typeof item !== "object");
}

function isNestedObject(value: unknown): value is Record<string, unknown> {
    return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function isWideField(key: string, value: unknown): boolean {
    return Array.isArray(value) || LONG_TEXT_FIELDS.has(key);
}

function sameValue(current: unknown, initial: unknown): boolean {
    if (Array.isArray(current) || Array.isArray(initial)) {
        return (
            Array.isArray(current) &&
            Array.isArray(initial) &&
            current.length === initial.length &&
            current.every((item, index) => sameValue(item, initial[index]))
        );
    }

    if (current && initial && typeof current === "object" && typeof initial === "object") {
        const currentRecord = current as Record<string, unknown>;
        const initialRecord = initial as Record<string, unknown>;

        return Object.keys(currentRecord).every((key) => sameValue(currentRecord[key], initialRecord[key]));
    }

    return current === initial;
}

function matchesInitial(values: Record<string, unknown>, initial: Record<string, unknown>): boolean {
    return Object.keys(values).every((key) => sameValue(values[key], initial[key]));
}

function mergeValues(base: unknown, override: unknown): unknown {
    if (!base || typeof base !== "object" || Array.isArray(base)) {
        return override ?? base;
    }

    if (!override || typeof override !== "object" || Array.isArray(override)) {
        return override ?? base;
    }

    const merged = { ...(base as Record<string, unknown>) };

    for (const [key, value] of Object.entries(override)) {
        if (value !== undefined) {
            merged[key] = mergeValues((base as Record<string, unknown>)[key], value);
        }
    }

    return merged;
}

function renderProviderTags(value: unknown): React.ReactNode {
    const labels = (Array.isArray(value) ? value : []).map((item, index) => {
        if (item && typeof item === "object") {
            const provider = item as Record<string, unknown>;
            return String(provider.displayName ?? provider.registrationId ?? `提供商 ${index + 1}`);
        }
        return String(item);
    });

    return <TagInput placeholder="暂无提供商" readOnly value={labels} />;
}

function renderControl(key: string, value: unknown, fieldKey: string): React.ReactNode {
    if (READ_ONLY_FIELDS.has(fieldKey)) {
        return <CopyableIdentifier mask value={typeof value === "string" ? value : ""} />;
    }

    if (OBJECT_ARRAY_FIELDS.has(key)) {
        return (
            <div className="w-full" style={{ maxWidth: PROVIDER_FIELD_MAX_WIDTH }}>
                {renderProviderTags(value)}
            </div>
        );
    }

    if (IMAGE_FIELDS.has(fieldKey)) {
        return <SiteImageField field={key as SiteAssetField} />;
    }

    if (typeof value === "boolean") {
        return <SwitchField description={FIELD_HELP[fieldKey] ?? SWITCH_FALLBACK_DESCRIPTION} />;
    }

    if (typeof value === "number") {
        return (
            <InputNumber
                decimalPlaces={0}
                min={0}
                style={{ maxWidth: NUMBER_FIELD_MAX_WIDTH, width: "100%" }}
                suffix={NUMBER_UNITS[key]}
            />
        );
    }

    const options = SELECT_OPTIONS[fieldKey];

    if (options) {
        return <Select options={options} placeholder="请选择" />;
    }

    if (fieldKey === "captchaProvider") {
        return <Select creatable filterable options={CAPTCHA_PROVIDER_OPTIONS} placeholder="选择或输入提供方" />;
    }

    if (isPrimitiveArray(value)) {
        return <TagInput placeholder="输入后回车添加" />;
    }

    if (LONG_TEXT_FIELDS.has(key)) {
        return <Textarea autosize={{ maxRows: 4, minRows: 2 }} />;
    }

    return SECRET_FIELD_PATTERN.test(key) ? <Input type="password" /> : <Input />;
}

function renderField(key: string, entry: unknown, path: (string | number)[]): React.ReactNode {
    const fieldPath = [...path, key];
    const fieldKey = fieldPath.join(".");
    const readOnly = READ_ONLY_FIELDS.has(fieldKey) || OBJECT_ARRAY_FIELDS.has(key);
    const booleanField = typeof entry === "boolean";

    if (!OBJECT_ARRAY_FIELDS.has(key) && isComplexArray(entry)) {
        return (
            <Form.FormItem
                className="!mb-0"
                key={fieldKey}
                label={renderFieldLabel(labelOf(key), FIELD_HELP[fieldKey])}
            >
                <ObjectDetails value={entry} />
            </Form.FormItem>
        );
    }

    return (
        <Form.FormItem
            className="!mb-0"
            key={fieldKey}
            label={renderFieldLabel(labelOf(key), booleanField ? undefined : FIELD_HELP[fieldKey])}
            name={readOnly ? undefined : fieldPath}
        >
            {renderControl(key, entry, fieldKey)}
        </Form.FormItem>
    );
}

function renderFields(
    value: Record<string, unknown>,
    path: (string | number)[] = [],
    layout: ParameterGroupLayout = {},
    depth = 0,
): React.ReactNode {
    const fullWidth: React.ReactNode[] = [];
    const ownRow: React.ReactNode[] = [];
    const compact: React.ReactNode[] = [];
    const wide: React.ReactNode[] = [];
    const sections: React.ReactNode[] = [];

    for (const [key, entry] of Object.entries(value)) {
        if (HIDDEN_FIELDS.has(key)) {
            continue;
        }

        if (isNestedObject(entry)) {
            if (Object.keys(entry).length === 0) {
                continue;
            }

            const fieldKey = [...path, key].join(".");
            sections.push(
                <section className="flex min-w-0 flex-col gap-3" key={fieldKey}>
                    {depth > 0 ? (
                        <h3 className="m-0 pl-10 text-[15px] leading-[1.8] font-medium text-[var(--td-text-color-primary)]">
                            {labelOf(key)}
                        </h3>
                    ) : (
                        <h2 className="m-0 pl-5 text-[16px] leading-[1.8] font-medium text-[var(--td-text-color-primary)]">
                            {labelOf(key)}
                        </h2>
                    )}
                    {renderFields(entry, [...path, key], layout, depth + 1)}
                </section>,
            );
            continue;
        }

        const fieldKey = [...path, key].join(".");
        const booleanFullWidth = typeof entry === "boolean" && Boolean(layout.booleanFullWidth);
        const wideField = isWideField(key, entry) && !layout.compactArrayFields?.has(key);
        const columns: FieldColumns = {
            ...(booleanFullWidth || wideField ? { xs: 12 } : { sm: 6, xs: 12 }),
            ...layout.fieldColumns?.[fieldKey],
        };
        const column = (
            <Col key={fieldKey} sm={columns.sm} xs={columns.xs}>
                {renderField(key, entry, path)}
            </Col>
        );

        if (booleanFullWidth) {
            fullWidth.push(column);
        } else if (layout.ownRowFields?.has(fieldKey)) {
            ownRow.push(column);
        } else if (wideField) {
            wide.push(column);
        } else {
            compact.push(column);
        }
    }

    const hasFields = ownRow.length > 0 || fullWidth.length > 0 || compact.length > 0 || wide.length > 0;
    const fieldIndent = depth === 0 ? "" : depth === 1 ? "pl-10" : "pl-14";

    return (
        <div className="flex min-w-0 flex-col gap-6">
            {hasFields ? (
                <div className={`flex min-w-0 flex-col gap-6 ${fieldIndent}`}>
                    {ownRow.length > 0 ? <Row gutter={FIELD_ROW_GUTTER}>{ownRow}</Row> : null}
                    {fullWidth.length > 0 ? <Row gutter={FIELD_ROW_GUTTER}>{fullWidth}</Row> : null}
                    {compact.length > 0 || wide.length > 0 ? (
                        <Row gutter={FIELD_ROW_GUTTER}>
                            {compact}
                            {wide}
                        </Row>
                    ) : null}
                </div>
            ) : null}
            {sections}
        </div>
    );
}

type ParameterGroupFormProps = {
    data: Record<string, unknown>;
    group: string;
    label: string;
};

function ParameterGroupForm({ data, group, label }: ParameterGroupFormProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [dirty, setDirty] = useState(false);
    const layout = PARAMETER_GROUP_LAYOUTS[group];
    const saveMutation = useMutation({
        mutationFn: async (values: Record<string, unknown>) => {
            await apiClient.parameterSetting.updateParameterSettingByGroup({
                body: mergeValues(data, values) as object,
                group,
            });
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            setDirty(false);
            prompt.success({ content: "参数已保存。" });
            void queryClient.invalidateQueries({ queryKey: [...PARAMETER_SETTINGS_QUERY_ROOT, group] });
        },
    });

    return (
        <SettingLayout
            footer={
                <IdentityDialogFooter
                    cancelLabel="重置"
                    disabled={!dirty}
                    onCancel={() => form.reset()}
                    onSubmit={() => form.submit()}
                    pending={saveMutation.isPending}
                    submitLabel="保存"
                />
            }
            title={`${label}参数`}
        >
            <Form
                form={form}
                initialData={data}
                labelAlign={layout?.labelAlign ?? "top"}
                labelWidth={layout?.labelWidth}
                layout="vertical"
                onValuesChange={(_, allValues) => setDirty(!matchesInitial(allValues as Record<string, unknown>, data))}
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        saveMutation.mutate(form.getFieldsValue(true) as Record<string, unknown>);
                    }
                }}
                resetType="initial"
            >
                {renderFields(data, [], layout)}
            </Form>
        </SettingLayout>
    );
}

function ParameterGroupEditor({ group, label }: { group: string; label: string }) {
    const prompt = useMessagePrompt();
    const query = useQuery({
        queryKey: [...PARAMETER_SETTINGS_QUERY_ROOT, group],
        queryFn: async () => (await apiClient.parameterSetting.getParameterSettingByGroup({ group })).data,
    });
    const unavailable = query.isSuccess && (!query.data || typeof query.data !== "object" || Array.isArray(query.data));

    useEffect(() => {
        if (query.isError) {
            prompt.error({ content: consoleErrorMessage(query.error) });
        }
    }, [prompt, query.error, query.isError]);

    let body: React.ReactNode;

    if (query.isPending) {
        body = <Skeleton animation="gradient" theme="paragraph" />;
    } else if (query.isError) {
        body = (
            <Empty
                action={<Button onClick={() => void query.refetch()}>重试</Button>}
                description="参数加载失败"
                type="network-error"
            />
        );
    } else if (unavailable) {
        body = <Empty description="暂无可编辑的参数" />;
    } else {
        return (
            <ParameterGroupForm
                data={query.data as unknown as Record<string, unknown>}
                group={group}
                key={query.dataUpdatedAt}
                label={label}
            />
        );
    }

    return <SettingLayout title={`${label}参数`}>{body}</SettingLayout>;
}

export function SettingsView() {
    const queryClient = useQueryClient();
    const [activeGroup, setActiveGroup] = useState(DEFAULT_PARAMETER_GROUP);
    const [visitedGroups, setVisitedGroups] = useState<Set<string>>(() => new Set([DEFAULT_PARAMETER_GROUP]));
    const refreshing = useIsFetching({ queryKey: PARAMETER_SETTINGS_QUERY_ROOT }) > 0;
    const selectGroup = (value: string | number | boolean) => {
        const group = String(value);
        setActiveGroup(group);
        setVisitedGroups((previous) => new Set(previous).add(group));
    };

    return (
        <div className="flex w-full min-w-0 flex-col gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <PageBreadcrumb current="参数配置" parents={["系统设置"]} />
                <div className="flex flex-wrap items-center gap-2">
                    <Button
                        icon={<RefreshIcon />}
                        loading={refreshing}
                        onClick={() => void queryClient.invalidateQueries({ queryKey: PARAMETER_SETTINGS_QUERY_ROOT })}
                        variant="outline"
                    >
                        刷新
                    </Button>
                </div>
            </div>

            <RadioGroup
                className="w-fit"
                onChange={selectGroup}
                options={PARAMETER_GROUPS.map((group) => ({ label: group.label, value: group.key }))}
                theme="button"
                value={activeGroup}
                variant="default-filled"
            />

            {PARAMETER_GROUPS.filter((group) => visitedGroups.has(group.key)).map((group) => (
                <div hidden={activeGroup !== group.key} key={group.key}>
                    <ParameterGroupEditor group={group.key} label={group.label} />
                </div>
            ))}
        </div>
    );
}
