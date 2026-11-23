"use client";

import { useMemo, useState } from "react";

import { UploadOutlined } from "@ant-design/icons";
import { type ProColumns, ProTable } from "@ant-design/pro-components";
import type { BatchCreateResponse, FeishuContactView } from "@pudans-dev/api-client";
import {
    Alert,
    Button,
    Empty,
    Flex,
    Input,
    Modal,
    Segmented,
    Select,
    Table,
    type TableProps,
    Tag,
    Typography,
    Upload,
    theme,
} from "antd";
import { ContactRound, FileSpreadsheet, Search, UsersRound } from "lucide-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useDownload } from "@/hooks/utils/use-download";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { feishuContactImportIssue, useFeishuContacts } from "../composables/_feishu-contacts";
import { useBatchCreateFeishuUsers } from "../composables/_feishu-create";
import {
    type FeishuUserDraft,
    MAX_USER_BATCH_SIZE,
    type UserDraftIssue,
    createFeishuUserCsvTemplate,
    feishuUsernameIssue,
    parseFeishuUserCsv,
    selectFailedFeishuUserDrafts,
} from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";
import { BatchCreateResult } from "./batch-create-result";
import { FeishuSyncNotice } from "./feishu-sync-notice";
import { UserAvatar } from "./user-avatar";

type ImportUsersModalProps = {
    groupOptions: UserGroupOption[];
    onClose: () => void;
};

type ImportSource = "feishu" | "csv";

type PreviewRow = FeishuUserDraft & {
    key: number;
};

export function ImportUsersModal({ groupOptions, onClose }: ImportUsersModalProps) {
    const prompt = useMessagePrompt();
    const { download } = useDownload();
    const { token } = theme.useToken();
    const [source, setSource] = useState<ImportSource>("feishu");
    const [fileName, setFileName] = useState("");
    const [rows, setRows] = useState<FeishuUserDraft[]>([]);
    const [issues, setIssues] = useState<UserDraftIssue[]>([]);
    const [parsing, setParsing] = useState(false);
    const [result, setResult] = useState<BatchCreateResponse>();
    const [contactKeyword, setContactKeyword] = useState("");
    const [selectedGroup, setSelectedGroup] = useState<string>();
    const [selectedContacts, setSelectedContacts] = useState<Map<string, FeishuContactView>>(new Map());
    const [contactUsernames, setContactUsernames] = useState<Map<string, string>>(new Map());
    const createUsers = useBatchCreateFeishuUsers();
    const contactsQuery = useFeishuContacts(source === "feishu");
    const contacts = useMemo(() => {
        const unique = new Map<string, FeishuContactView>();

        contactsQuery.data?.pages.forEach((page) => {
            page.items?.forEach((contact) => {
                if (contact.providerUserId) {
                    unique.set(contact.providerUserId, contact);
                }
            });
        });

        return [...unique.values()];
    }, [contactsQuery.data?.pages]);
    const filteredContacts = useMemo(() => {
        const keyword = contactKeyword.trim().toLowerCase();

        if (!keyword) {
            return contacts;
        }

        return contacts.filter((contact) => {
            const searchable = [
                contact.displayName,
                contact.email,
                contact.phone,
                contact.providerUserId,
                contact.importedUsername,
            ];
            return searchable.some((value) => value?.toLowerCase().includes(keyword));
        });
    }, [contactKeyword, contacts]);
    const selectedContactRows = useMemo(() => [...selectedContacts.values()], [selectedContacts]);
    const importedContactCount = useMemo(() => contacts.filter((contact) => contact.imported).length, [contacts]);
    const importableContactCount = useMemo(
        () => contacts.filter((contact) => !feishuContactImportIssue(contact)).length,
        [contacts],
    );
    const importableSelectedContacts = useMemo(
        () => selectedContactRows.filter((contact) => !feishuContactImportIssue(contact)),
        [selectedContactRows],
    );
    const hasInvalidSelectedUsername = useMemo(
        () =>
            importableSelectedContacts.some((contact) => {
                const providerUserId = contact.providerUserId;
                return Boolean(providerUserId && feishuUsernameIssue(contactUsernames.get(providerUserId)));
            }),
        [contactUsernames, importableSelectedContacts],
    );
    const importCount = source === "feishu" ? importableSelectedContacts.length : rows.length;
    const canSubmit =
        importCount > 0 &&
        issues.length === 0 &&
        !hasInvalidSelectedUsername &&
        !parsing &&
        !createUsers.isPending &&
        (source === "feishu" || rows.length > 0);

    const resetCsv = () => {
        setFileName("");
        setRows([]);
        setIssues([]);
    };
    const changeSource = (value: string | number) => {
        const next = value as ImportSource;

        if (next === source) {
            return;
        }

        setSource(next);
        setResult(undefined);
        setContactKeyword("");
        setSelectedContacts(new Map());
        setContactUsernames(new Map());
        setSelectedGroup(undefined);
        resetCsv();
    };
    const handleFile = async (file: File) => {
        setParsing(true);
        setResult(undefined);

        try {
            const parsed = await parseFeishuUserCsv(file);
            setFileName(parsed.fileName);
            setRows(parsed.rows);
            setIssues(parsed.issues);
        } catch (error) {
            resetCsv();
            prompt.error(error instanceof Error ? error.message : "CSV 解析失败。");
        } finally {
            setParsing(false);
        }
    };
    const clearSelection = () => setSelectedContacts(new Map());
    const submit = () => {
        const submitted =
            source === "feishu"
                ? importableSelectedContacts.map((contact) => ({
                      providerUserId: contact.providerUserId as string,
                      userGroup: selectedGroup,
                      username: contactUsernames.get(contact.providerUserId as string),
                  }))
                : rows;

        if (submitted.length === 0 || issues.length > 0) {
            return;
        }

        createUsers.mutate(submitted, {
            onSuccess: (response, submittedUsers) => {
                const created = response.created ?? 0;
                const failed = response.failed ?? 0;
                const retryRows = selectFailedFeishuUserDrafts(submittedUsers, response.failures);

                setResult(response);

                if (source === "feishu") {
                    const retryContacts = new Map<string, FeishuContactView>();
                    retryRows.forEach((row) => {
                        const contact = selectedContacts.get(row.providerUserId);
                        if (contact) {
                            retryContacts.set(row.providerUserId, contact);
                        }
                    });
                    setSelectedContacts(retryContacts);
                } else {
                    setRows(retryRows);
                }

                if (failed === 0) {
                    prompt.success(`已导入 ${created} 个用户。`);
                    onClose();
                    return;
                }

                prompt.warning(`已导入 ${created} 个用户，${failed} 个未能导入；失败成员已保留，可重试。`);
            },
        });
    };
    const downloadTemplate = () => {
        download(createFeishuUserCsvTemplate(), "feishu-users-import-template.csv", "text/csv;charset=utf-8");
    };
    const contactRowSelection: TableProps<FeishuContactView>["rowSelection"] = {
        getCheckboxProps: (contact) => ({ disabled: Boolean(feishuContactImportIssue(contact)) }),
        onChange: (keys, selectedRows) => {
            const selectedKeys = new Set(keys.map(String));
            setSelectedContacts((previous) => {
                const next = new Map(previous);

                contacts.forEach((contact) => {
                    if (contact.providerUserId && !selectedKeys.has(contact.providerUserId)) {
                        next.delete(contact.providerUserId);
                    }
                });
                selectedRows.forEach((contact) => {
                    if (
                        contact.providerUserId &&
                        selectedKeys.has(contact.providerUserId) &&
                        !feishuContactImportIssue(contact)
                    ) {
                        next.set(contact.providerUserId, contact);
                    }
                });

                return next;
            });
        },
        preserveSelectedRowKeys: true,
        selectedRowKeys: selectedContactRows
            .map((contact) => contact.providerUserId)
            .filter((providerUserId): providerUserId is string => Boolean(providerUserId)),
    };
    const contactColumns: TableProps<FeishuContactView>["columns"] = [
        {
            dataIndex: "displayName",
            render: (_, contact) => (
                <Flex align="center" gap={12}>
                    <UserAvatar name={contact.displayName} size={36} src={contact.avatar} />
                    <div className="min-w-0 truncate font-medium">{contact.displayName || "未命名成员"}</div>
                </Flex>
            ),
            title: "成员",
            width: 170,
        },
        {
            dataIndex: "email",
            render: (_, contact) => {
                return contact.email?.trim() ? (
                    <Typography.Text className="block max-w-[160px]" ellipsis>
                        {contact.email}
                    </Typography.Text>
                ) : (
                    <Tag className="!mr-0" color="warning">
                        飞书未提供
                    </Tag>
                );
            },
            title: "邮箱",
            width: 190,
        },
        {
            dataIndex: "providerUserId",
            render: (_, contact) => <CopyableIdentifier className="max-w-[190px]" value={contact.providerUserId} />,
            title: "飞书用户编号",
            width: 200,
        },
        {
            dataIndex: "providerUserId",
            render: (_, contact) => {
                if (contact.imported) {
                    return contact.importedUsername ? (
                        <Typography.Text
                            className="block max-w-[150px]"
                            copyable={{ text: contact.importedUsername }}
                            ellipsis={{ tooltip: contact.importedUsername }}
                        >
                            @{contact.importedUsername}
                        </Typography.Text>
                    ) : (
                        <EmptyValue text="未填写" />
                    );
                }

                const providerUserId = contact.providerUserId;
                const username = providerUserId ? contactUsernames.get(providerUserId) : undefined;
                const usernameIssue = feishuUsernameIssue(username);
                const contactIssue = feishuContactImportIssue(contact);

                return (
                    <div className="min-w-0">
                        <Input
                            allowClear
                            aria-label={`设置 ${contact.displayName || "该成员"} 的登录名`}
                            disabled={Boolean(contactIssue)}
                            maxLength={63}
                            onChange={(event) => {
                                if (!providerUserId) {
                                    return;
                                }

                                const nextUsername = event.target.value.toLowerCase();
                                setContactUsernames((previous) => {
                                    const next = new Map(previous);

                                    if (nextUsername) {
                                        next.set(providerUserId, nextUsername);
                                    } else {
                                        next.delete(providerUserId);
                                    }

                                    return next;
                                });
                            }}
                            placeholder={contactIssue ? "不可导入" : "留空自动生成"}
                            size="small"
                            status={usernameIssue ? "error" : undefined}
                            value={username ?? ""}
                        />
                        {usernameIssue ? (
                            <Typography.Text className="mt-1 block text-xs" type="danger">
                                {usernameIssue}
                            </Typography.Text>
                        ) : null}
                    </div>
                );
            },
            title: "登录名",
            width: 185,
        },
        {
            dataIndex: "providerUserId",
            render: (_, contact) => {
                if (contact.imported) {
                    return (
                        <Tag className="!mr-0" color="success">
                            已导入
                        </Tag>
                    );
                }
                return feishuContactImportIssue(contact) ? (
                    <Tag className="!mr-0" color="warning">
                        资料不完整
                    </Tag>
                ) : (
                    <Tag className="!mr-0" color="processing">
                        可导入
                    </Tag>
                );
            },
            title: "状态",
            width: 150,
        },
    ];
    const previewRows: PreviewRow[] = rows.map((row, index) => ({ ...row, key: index + 1 }));
    const previewColumns: ProColumns<PreviewRow>[] = [
        { dataIndex: "key", title: "序号", width: 64 },
        {
            dataIndex: "providerUserId",
            render: (_, row) => <CopyableIdentifier className="max-w-[280px]" value={row.providerUserId} />,
            title: "飞书用户 ID",
            width: 320,
        },
        {
            dataIndex: "username",
            render: (_, row) =>
                row.username ? (
                    <span className="font-mono text-xs">@{row.username}</span>
                ) : (
                    <EmptyValue text="自动生成" />
                ),
            title: "登录名",
            width: 160,
        },
        {
            dataIndex: "userGroup",
            render: (_, row) => row.userGroup || <EmptyValue text="默认用户组" />,
            title: "用户组",
            width: 160,
        },
    ];
    const visibleIssues = issues.slice(0, 6);
    const hasMoreContacts = contactsQuery.hasNextPage;

    return (
        <Modal
            destroyOnHidden
            footer={
                <Flex align="center" gap={12} justify="space-between" wrap>
                    <div>
                        {source === "csv" ? (
                            <Button className="!px-0" onClick={downloadTemplate} type="link">
                                下载 CSV 模板
                            </Button>
                        ) : (
                            <Typography.Text type="secondary">
                                已导入的成员不可重复导入，缺少姓名的成员无法导入
                            </Typography.Text>
                        )}
                    </div>
                    <Flex gap={8}>
                        <Button disabled={createUsers.isPending} onClick={onClose}>
                            取消
                        </Button>
                        <Button disabled={!canSubmit} loading={createUsers.isPending} onClick={submit} type="primary">
                            {importCount > 0 ? `导入 ${importCount} 个用户` : "导入用户"}
                        </Button>
                    </Flex>
                </Flex>
            }
            mask={{ closable: !createUsers.isPending }}
            onCancel={() => {
                if (!createUsers.isPending) {
                    onClose();
                }
            }}
            open
            title="导入用户"
            width={1000}
        >
            <Flex gap={20} vertical>
                <Segmented
                    block
                    onChange={changeSource}
                    options={[
                        {
                            label: (
                                <span className="inline-flex items-center gap-2">
                                    <UsersRound size={15} />
                                    从飞书导入
                                </span>
                            ),
                            value: "feishu",
                        },
                        {
                            label: (
                                <span className="inline-flex items-center gap-2">
                                    <FileSpreadsheet size={15} />从 CSV 导入
                                </span>
                            ),
                            value: "csv",
                        },
                    ]}
                    value={source}
                />
                {source === "feishu" ? (
                    <Flex gap={12} vertical>
                        <FeishuSyncNotice description="选择成员后，姓名、邮箱、手机号和头像由飞书同步。" />
                        <div
                            className="overflow-hidden rounded-2xl border"
                            style={{ borderColor: token.colorBorderSecondary }}
                        >
                            <Flex align="center" className="!px-4 !pt-4" gap={12} justify="space-between" wrap>
                                <Input
                                    allowClear
                                    aria-label="搜索飞书成员"
                                    className="w-full sm:!w-72"
                                    onChange={(event) => setContactKeyword(event.target.value)}
                                    placeholder="搜索姓名、邮箱或用户编号"
                                    prefix={<Search className="text-neutral-400" size={15} />}
                                    value={contactKeyword}
                                />
                                <Typography.Text type="secondary">
                                    已加载 {contacts.length} 位成员 · {importedContactCount} 位已导入 ·{" "}
                                    {importableContactCount} 位可导入
                                </Typography.Text>
                            </Flex>
                            <Table<FeishuContactView>
                                columns={contactColumns}
                                dataSource={filteredContacts}
                                loading={contactsQuery.isPending || contactsQuery.isFetchingNextPage}
                                locale={{
                                    emptyText: (
                                        <Empty
                                            description={contactKeyword ? "没有匹配的飞书成员" : "飞书通讯录暂无成员"}
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        />
                                    ),
                                }}
                                pagination={false}
                                rowKey={(contact) => contact.providerUserId ?? ""}
                                rowSelection={contactRowSelection}
                                scroll={{ x: 940, y: 320 }}
                                size="middle"
                            />
                            <Flex align="center" className="!px-4 !pb-4" gap={12} justify="space-between" wrap>
                                <div>
                                    {hasMoreContacts ? (
                                        <Button
                                            loading={contactsQuery.isFetchingNextPage}
                                            onClick={() => void contactsQuery.fetchNextPage()}
                                            type="link"
                                        >
                                            加载更多飞书成员
                                        </Button>
                                    ) : (
                                        <Typography.Text type="secondary">
                                            {contacts.length > 0 ? "已加载全部成员" : "飞书通讯录暂无可见成员"}
                                        </Typography.Text>
                                    )}
                                </div>
                                {selectedContactRows.length > 0 ? (
                                    <Button onClick={clearSelection} size="small" type="text">
                                        清空选择
                                    </Button>
                                ) : null}
                            </Flex>
                        </div>
                        {contactsQuery.error ? (
                            <Alert
                                action={
                                    <Button onClick={() => void contactsQuery.refetch()} size="small">
                                        重试
                                    </Button>
                                }
                                description={consoleErrorMessage(contactsQuery.error)}
                                message="飞书通讯录加载失败"
                                showIcon
                                type="error"
                            />
                        ) : null}
                        <Flex
                            align="center"
                            className="rounded-xl !px-4 !py-3"
                            gap={12}
                            style={{ backgroundColor: token.colorFillQuaternary }}
                            wrap
                        >
                            <div className="flex items-center gap-2">
                                <ContactRound size={15} />
                                <Typography.Text strong>导入到用户组</Typography.Text>
                            </div>
                            <Select
                                allowClear
                                aria-label="选择导入后的用户组"
                                className="w-full sm:w-64"
                                onChange={setSelectedGroup}
                                optionFilterProp="label"
                                options={groupOptions}
                                placeholder="使用默认用户组"
                                showSearch
                                value={selectedGroup}
                            />
                            <Typography.Text type="secondary">登录名留空时由系统自动生成</Typography.Text>
                        </Flex>
                    </Flex>
                ) : (
                    <Flex gap={14} vertical>
                        <FeishuSyncNotice description="CSV 只需包含飞书用户 ID，其余资料由飞书同步。" />
                        <div
                            className="rounded-2xl border border-dashed p-5"
                            style={{ borderColor: token.colorBorder }}
                        >
                            <Flex align="center" gap={16} justify="space-between" wrap>
                                <div className="min-w-0">
                                    <Typography.Text strong>上传 CSV 文件</Typography.Text>
                                    <Typography.Text className="mt-1 block text-xs" type="secondary">
                                        支持 feishuId、unionId、openId 表头，单次最多 {MAX_USER_BATCH_SIZE} 个用户。
                                    </Typography.Text>
                                </div>
                                <Upload
                                    accept=".csv,text/csv"
                                    beforeUpload={(file) => {
                                        void handleFile(file);
                                        return false;
                                    }}
                                    disabled={parsing || createUsers.isPending}
                                    maxCount={1}
                                    showUploadList={false}
                                >
                                    <Button icon={<UploadOutlined />} loading={parsing}>
                                        {fileName ? "重新选择" : "选择文件"}
                                    </Button>
                                </Upload>
                            </Flex>
                        </div>
                        {fileName ? (
                            <Flex align="center" gap={8} wrap>
                                <Typography.Text className="min-w-0" ellipsis>
                                    {fileName}
                                </Typography.Text>
                                <Tag>{rows.length} 条记录</Tag>
                                {issues.length > 0 ? (
                                    <Tag color="error">{issues.length} 个问题</Tag>
                                ) : (
                                    <Tag color="success">校验通过</Tag>
                                )}
                                <Button onClick={resetCsv} size="small" type="text">
                                    移除
                                </Button>
                            </Flex>
                        ) : null}
                        {visibleIssues.length > 0 ? (
                            <Alert
                                description={
                                    <div className="mt-1 grid gap-1 text-xs">
                                        {visibleIssues.map((issue) => (
                                            <div
                                                key={`${issue.index}-${issue.providerUserId ?? "unknown"}-${issue.message}`}
                                            >
                                                {issue.index > 0 ? `第 ${issue.index} 条：` : ""}
                                                {issue.message}
                                            </div>
                                        ))}
                                        {issues.length > visibleIssues.length ? (
                                            <div>另有 {issues.length - visibleIssues.length} 个问题。</div>
                                        ) : null}
                                    </div>
                                }
                                message={`发现 ${issues.length} 个问题`}
                                showIcon
                                type="error"
                            />
                        ) : null}
                        {rows.length > 0 ? (
                            <ProTable<PreviewRow>
                                columns={previewColumns}
                                dataSource={previewRows}
                                ghost
                                loading={createUsers.isPending}
                                options={false}
                                pagination={false}
                                rowKey="key"
                                scroll={{ x: "max-content", y: 280 }}
                                search={false}
                                size="small"
                                toolBarRender={false}
                            />
                        ) : null}
                    </Flex>
                )}
                {result ? <BatchCreateResult {...result} /> : null}
            </Flex>
        </Modal>
    );
}
