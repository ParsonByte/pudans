"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Divider, Empty, Form, Input, InputNumber, Skeleton, Switch, Tabs, TagInput } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";

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
    accessTokenTtlSeconds: "访问令牌有效期（秒）",
    announcementVersion: "公告版本",
    argon2PasswordStorageEnabled: "Argon2 密码存储",
    auditRecordRules: "审计记录规则",
    authentication: "认证",
    avatarDimension: "头像尺寸",
    avatarMaxSize: "头像最大字节数",
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
    downloadTokenTtlSeconds: "下载令牌有效期（秒）",
    exposeEmailToSignedInUsers: "向登录用户公开邮箱",
    favicon: "站点图标",
    feishu: "飞书",
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
    maxLifetimeDays: "最大有效期（天）",
    mode: "模式",
    oauth2: "OAuth2",
    oidc: "OIDC",
    operations: "操作",
    passkeyLoginEnabled: "通行密钥登录",
    pat: "个人访问令牌",
    providers: "提供商",
    recoveryCaptchaEnabled: "找回密码图片验证码",
    refreshTokenTtlSeconds: "刷新令牌有效期（秒）",
    resourceTypes: "资源类型",
    rsaLoginEncryptionEnabled: "RSA 登录加密",
    siteAnnouncement: "站点公告",
    siteDescription: "站点描述",
    siteId: "站点 ID",
    siteName: "站点名称",
    siteUrl: "站点地址",
    smtpConnectionTtlSeconds: "SMTP 连接有效期（秒）",
    smtpForceSsl: "强制 SSL",
    smtpHost: "SMTP 主机",
    smtpPort: "SMTP 端口",
    tempPath: "临时目录",
    totp: "TOTP",
    trashRetentionDays: "回收站保留天数",
    uploadSessionTtlHours: "上传会话有效期（小时）",
    enabled: "启用",
};

const SECRET_FIELD_PATTERN = /password|secret|token|key/i;

function labelOf(key: string): string {
    return FIELD_LABELS[key] ?? key;
}

function isComplexArray(value: unknown): boolean {
    return Array.isArray(value) && value.some((item) => item !== null && typeof item === "object");
}

function isPrimitiveArray(value: unknown): boolean {
    return Array.isArray(value) && value.every((item) => item === null || typeof item !== "object");
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

function renderControl(key: string, value: unknown): React.ReactNode {
    if (typeof value === "boolean") {
        return <Switch />;
    }

    if (typeof value === "number") {
        return <InputNumber style={{ width: "100%" }} />;
    }

    if (isPrimitiveArray(value)) {
        return <TagInput />;
    }

    return SECRET_FIELD_PATTERN.test(key) ? <Input type="password" /> : <Input />;
}

function renderFields(value: Record<string, unknown>, path: (string | number)[] = []): React.ReactNode {
    return Object.entries(value).map(([key, entry]) => {
        const fieldPath = [...path, key];

        if (isComplexArray(entry)) {
            return (
                <Form.FormItem key={fieldPath.join(".")} label={labelOf(key)}>
                    <ObjectDetails value={entry} />
                </Form.FormItem>
            );
        }

        if (entry && typeof entry === "object" && !Array.isArray(entry)) {
            return (
                <div key={fieldPath.join(".")}>
                    <Divider align="left">{labelOf(key)}</Divider>
                    {renderFields(entry as Record<string, unknown>, fieldPath)}
                </div>
            );
        }

        return (
            <Form.FormItem key={fieldPath.join(".")} label={labelOf(key)} name={fieldPath}>
                {renderControl(key, entry)}
            </Form.FormItem>
        );
    });
}

type ParameterGroupFormProps = {
    data: Record<string, unknown>;
    group: string;
};

function ParameterGroupForm({ data, group }: ParameterGroupFormProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [dirty, setDirty] = useState(false);
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
            void queryClient.invalidateQueries({ queryKey: ["console", "parameter-settings", group] });
        },
    });

    return (
        <Form
            form={form}
            initialData={data}
            labelAlign="top"
            layout="vertical"
            onValuesChange={() => setDirty(true)}
            onSubmit={({ validateResult }) => {
                if (validateResult === true) {
                    saveMutation.mutate(form.getFieldsValue(true) as Record<string, unknown>);
                }
            }}
            resetType="initial"
        >
            <div className="flex flex-col gap-2">
                {renderFields(data)}
                <div className="flex gap-2 pt-2">
                    <Button
                        disabled={!dirty}
                        loading={saveMutation.isPending}
                        onClick={() => form.submit()}
                        theme="primary"
                    >
                        保存
                    </Button>
                    <Button
                        disabled={!dirty}
                        onClick={() => {
                            form.reset();
                            setDirty(false);
                        }}
                        variant="outline"
                    >
                        重置
                    </Button>
                </div>
            </div>
        </Form>
    );
}

function ParameterGroupEditor({ group }: { group: string }) {
    const prompt = useMessagePrompt();
    const query = useQuery({
        queryKey: ["console", "parameter-settings", group],
        queryFn: async () => (await apiClient.parameterSetting.getParameterSettingByGroup({ group })).data,
    });
    const unavailable = query.isSuccess && (!query.data || typeof query.data !== "object" || Array.isArray(query.data));

    useEffect(() => {
        if (query.isError) {
            prompt.error({ content: consoleErrorMessage(query.error) });
        }
    }, [prompt, query.error, query.isError]);

    if (query.isPending) {
        return <Skeleton animation="gradient" theme="paragraph" />;
    }

    if (query.isError) {
        return (
            <Empty
                action={<Button onClick={() => void query.refetch()}>重试</Button>}
                description="参数加载失败"
                type="network-error"
            />
        );
    }

    if (unavailable) {
        return <Empty description="暂无可编辑的参数" />;
    }

    return (
        <ParameterGroupForm
            data={query.data as unknown as Record<string, unknown>}
            group={group}
            key={query.dataUpdatedAt}
        />
    );
}

export function SettingsView() {
    return (
        <Tabs
            defaultValue="site"
            list={PARAMETER_GROUPS.map((group) => ({
                label: group.label,
                lazy: true,
                panel: <ParameterGroupEditor group={group.key} />,
                value: group.key,
            }))}
        />
    );
}
