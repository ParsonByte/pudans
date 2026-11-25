"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import type { BatchCreateResponse, FeishuContactView } from "@pudans-dev/api-client";
import { FileCsvIcon, SearchIcon, UploadIcon, UserBusinessIcon } from "tdesign-icons-react";
import { Alert, Button, Empty, Form, Input, Radio, Select, Table, Tag, Upload } from "tdesign-react";
import type { TableProps, UploadFile } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useDownload } from "@/hooks/utils/use-download";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { feishuContactImportIssue, useFeishuContacts } from "../composables/_feishu-contacts";
import { useBatchCreateFeishuUsers } from "../composables/_feishu-create";
import {
    type FeishuUserDraft,
    type UserDraftIssue,
    createFeishuUserCsvTemplate,
    feishuUsernameIssue,
    parseFeishuUserCsv,
    selectFailedFeishuUserDrafts,
} from "../composables/_feishu-users";
import type { UserGroupOption } from "../composables/_user-groups";
import { BatchCreateResult } from "./batch-create-result";
import { UserAvatar } from "./user-avatar";

type ImportUsersModalProps = {
    groupOptions: UserGroupOption[];
    onClose: () => void;
};

type ImportSource = "feishu" | "csv";

type ContactRow = FeishuContactView & {
    __rowKey: string;
};

type PreviewRow = FeishuUserDraft & {
    key: number;
};

const SOURCE_OPTIONS = [
    {
        label: (
            <span className="inline-flex items-center gap-1.5">
                <UserBusinessIcon />
                从飞书导入
            </span>
        ),
        value: "feishu",
    },
    {
        label: (
            <span className="inline-flex items-center gap-1.5">
                <FileCsvIcon />从 CSV 导入
            </span>
        ),
        value: "csv",
    },
];

export function ImportUsersModal({ groupOptions, onClose }: ImportUsersModalProps) {
    const prompt = useMessagePrompt();
    const { download } = useDownload();
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
    const { error: contactsError, refetch: refetchContacts } = contactsQuery;
    const reportedContactsError = useRef<unknown>(null);

    useEffect(() => {
        if (!contactsError) {
            reportedContactsError.current = null;
            return;
        }

        if (reportedContactsError.current === contactsError) {
            return;
        }

        // 同一错误只提示一次，重试再次失败会生成新的错误对象
        reportedContactsError.current = contactsError;
        prompt.error({
            content: consoleErrorMessage(contactsError),
            footer: (
                <Button onClick={() => void refetchContacts()} size="small" variant="outline">
                    重试
                </Button>
            ),
            title: "飞书通讯录加载失败",
        });
    }, [contactsError, refetchContacts, prompt]);

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
    const contactRows = useMemo<ContactRow[]>(
        () =>
            contacts.map((contact) => ({
                ...contact,
                __rowKey: contact.providerUserId as string,
            })),
        [contacts],
    );
    const filteredContacts = useMemo(() => {
        const keyword = contactKeyword.trim().toLowerCase();

        if (!keyword) {
            return contactRows;
        }

        return contactRows.filter((contact) => {
            const searchable = [
                contact.displayName,
                contact.email,
                contact.phone,
                contact.providerUserId,
                contact.importedUsername,
            ];
            return searchable.some((value) => value?.toLowerCase().includes(keyword));
        });
    }, [contactKeyword, contactRows]);
    const selectedContactRows = useMemo(() => [...selectedContacts.values()], [selectedContacts]);
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
    const changeSource = (value: string | number | boolean) => {
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
    const close = () => {
        if (!createUsers.isPending) {
            onClose();
        }
    };
    const handleContactSelect = (keys: Array<string | number>) => {
        const selectedKeys = new Set(keys.map(String));

        setSelectedContacts((previous) => {
            const next = new Map(previous);

            contacts.forEach((contact) => {
                const providerUserId = contact.providerUserId;
                if (providerUserId && !selectedKeys.has(providerUserId)) {
                    next.delete(providerUserId);
                }
            });
            filteredContacts.forEach((contact) => {
                const providerUserId = contact.providerUserId;
                if (providerUserId && selectedKeys.has(providerUserId) && !feishuContactImportIssue(contact)) {
                    next.set(providerUserId, contact);
                }
            });

            return next;
        });
    };
    const contactColumns: NonNullable<TableProps<ContactRow>["columns"]> = [
        {
            colKey: "row-select",
            disabled: ({ row }) => Boolean(feishuContactImportIssue(row)),
            type: "multiple",
            width: 48,
        },
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 items-center gap-2.5">
                    <UserAvatar name={row.displayName} size={34} src={row.avatar} />
                    <div className="min-w-0">
                        <div className="truncate font-medium">{row.displayName || "未命名成员"}</div>
                        {row.email?.trim() ? (
                            <div className="truncate text-xs text-[var(--td-text-color-secondary)]">{row.email}</div>
                        ) : null}
                    </div>
                </div>
            ),
            colKey: "displayName",
            title: "成员",
            width: 180,
        },
        {
            cell: ({ row }) => <CopyableIdentifier mask value={row.providerUserId} />,
            colKey: "providerUserId",
            title: "飞书用户编号",
            width: 170,
        },
        {
            cell: ({ row }) => {
                if (row.imported) {
                    return row.importedUsername ? (
                        <CopyableIdentifier className="max-w-36" value={row.importedUsername} />
                    ) : (
                        <EmptyValue />
                    );
                }

                const providerUserId = row.providerUserId;
                const username = providerUserId ? contactUsernames.get(providerUserId) : undefined;
                const usernameIssue = feishuUsernameIssue(username);
                const contactIssue = feishuContactImportIssue(row);

                return (
                    <div className="min-w-0">
                        <Input
                            clearable
                            disabled={Boolean(contactIssue)}
                            maxlength={63}
                            onChange={(value) => {
                                if (!providerUserId) {
                                    return;
                                }

                                const nextUsername = String(value).toLowerCase();
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
                            <span className="mt-1 block text-xs text-[var(--td-error-color)]">{usernameIssue}</span>
                        ) : null}
                    </div>
                );
            },
            colKey: "username",
            title: "登录名",
            width: 150,
        },
        {
            cell: ({ row }) => {
                if (row.imported) {
                    return (
                        <Tag theme="success" variant="light">
                            已导入
                        </Tag>
                    );
                }
                return feishuContactImportIssue(row) ? (
                    <Tag theme="warning" variant="light">
                        资料不完整
                    </Tag>
                ) : (
                    <Tag theme="primary" variant="light">
                        可导入
                    </Tag>
                );
            },
            colKey: "status",
            title: "状态",
            width: 120,
        },
    ];
    const previewRows: PreviewRow[] = rows.map((row, index) => ({ ...row, key: index + 1 }));
    const previewColumns: NonNullable<TableProps<PreviewRow>["columns"]> = [
        { colKey: "key", title: "序号", width: 64 },
        {
            cell: ({ row }) => <CopyableIdentifier mask value={row.providerUserId} />,
            colKey: "providerUserId",
            title: "飞书用户 ID",
            width: 300,
        },
        {
            cell: ({ row }) =>
                row.username ? (
                    <span className="font-mono text-xs">@{row.username}</span>
                ) : (
                    <EmptyValue text="自动生成" />
                ),
            colKey: "username",
            title: "登录名",
            width: 160,
        },
        {
            cell: ({ row }) => row.userGroup || <EmptyValue text="默认用户组" />,
            colKey: "userGroup",
            title: "用户组",
            width: 150,
        },
    ];
    const visibleIssues = issues.slice(0, 6);
    const hasMoreContacts = contactsQuery.hasNextPage;
    const contactEmptyDescription = contactKeyword ? "没有匹配的飞书成员" : "飞书通讯录暂无成员";

    return (
        <GlobalDialog
            footer={
                <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        {source === "csv" ? (
                            <Button onClick={downloadTemplate} variant="text">
                                下载 CSV 模板
                            </Button>
                        ) : null}
                    </div>
                    <IdentityDialogFooter
                        disabled={!canSubmit}
                        onCancel={close}
                        onSubmit={submit}
                        pending={createUsers.isPending}
                        submitLabel={importCount > 0 ? `导入 ${importCount} 个用户` : "导入用户"}
                    />
                </div>
            }
            onClose={close}
            open
            title="导入用户"
            width={760}
        >
            <div className="flex flex-col gap-5">
                <Radio.Group
                    onChange={changeSource}
                    options={SOURCE_OPTIONS}
                    theme="button"
                    value={source}
                    variant="default-filled"
                />
                {source === "feishu" ? (
                    <Form labelAlign="top">
                        <Form.FormItem label="搜索成员">
                            <Input
                                clearable
                                onChange={(value) => setContactKeyword(String(value))}
                                placeholder="搜索姓名、邮箱或用户编号"
                                suffixIcon={<SearchIcon />}
                                value={contactKeyword}
                            />
                        </Form.FormItem>
                        <Form.FormItem label="飞书成员">
                            <div className="flex flex-col gap-2">
                                <div className="flex flex-wrap items-center justify-end gap-3">
                                    <span className="text-xs text-[var(--td-text-color-secondary)]">
                                        可导入{" "}
                                        {contactRows.filter((contact) => !feishuContactImportIssue(contact)).length}
                                    </span>
                                </div>
                                <Table<ContactRow>
                                    columns={contactColumns}
                                    data={filteredContacts}
                                    empty={
                                        <Empty
                                            description={contactsQuery.error ? undefined : contactEmptyDescription}
                                        />
                                    }
                                    loading={contactsQuery.isPending || contactsQuery.isFetchingNextPage}
                                    maxHeight={320}
                                    onSelectChange={handleContactSelect}
                                    rowKey="__rowKey"
                                    selectedRowKeys={selectedContactRows
                                        .map((contact) => contact.providerUserId)
                                        .filter((providerUserId): providerUserId is string => Boolean(providerUserId))}
                                />
                                <div className="flex flex-wrap items-center justify-between gap-3">
                                    <div>
                                        {hasMoreContacts ? (
                                            <Button
                                                loading={contactsQuery.isFetchingNextPage}
                                                onClick={() => void contactsQuery.fetchNextPage()}
                                                variant="text"
                                            >
                                                加载更多飞书成员
                                            </Button>
                                        ) : null}
                                    </div>
                                </div>
                            </div>
                        </Form.FormItem>
                        <Form.FormItem className="mt-4" label="用户组">
                            <Select
                                clearable
                                filterable
                                onChange={(value) => {
                                    setSelectedGroup(value ? String(value) : undefined);
                                }}
                                options={groupOptions}
                                placeholder="使用默认用户组"
                                value={selectedGroup}
                            />
                        </Form.FormItem>
                    </Form>
                ) : (
                    <div className="flex flex-col gap-4">
                        <Upload
                            accept=".csv,text/csv"
                            autoUpload={false}
                            beforeUpload={(file: UploadFile) => {
                                if (file.raw) {
                                    void handleFile(file.raw);
                                }
                                return false;
                            }}
                            disabled={parsing || createUsers.isPending}
                            max={1}
                            theme="custom"
                        >
                            <Button
                                disabled={parsing || createUsers.isPending}
                                icon={<UploadIcon />}
                                loading={parsing}
                                variant="outline"
                            >
                                {fileName ? "重新选择" : "选择文件"}
                            </Button>
                        </Upload>
                        {fileName ? (
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="min-w-0 truncate">{fileName}</span>
                                <Tag>{rows.length} 条记录</Tag>
                                {issues.length > 0 ? (
                                    <Tag theme="danger" variant="light">
                                        {issues.length} 个问题
                                    </Tag>
                                ) : (
                                    <Tag theme="success" variant="light">
                                        校验通过
                                    </Tag>
                                )}
                                <Button onClick={resetCsv} size="small" variant="text">
                                    移除
                                </Button>
                            </div>
                        ) : null}
                        {visibleIssues.length > 0 ? (
                            <Alert
                                message={
                                    <div className="grid gap-1 text-xs">
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
                                theme="error"
                                title={`发现 ${issues.length} 个问题`}
                            />
                        ) : null}
                        {rows.length > 0 ? (
                            <Table<PreviewRow>
                                columns={previewColumns}
                                data={previewRows}
                                loading={createUsers.isPending}
                                maxHeight={280}
                                rowKey="key"
                                size="small"
                            />
                        ) : null}
                    </div>
                )}
                {result ? <BatchCreateResult {...result} /> : null}
            </div>
        </GlobalDialog>
    );
}
