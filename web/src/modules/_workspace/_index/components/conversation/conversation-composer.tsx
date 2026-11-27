"use client";

import { type ChangeEvent, useEffect, useRef, useState } from "react";

import dynamic from "next/dynamic";

import type { TdAttachmentItem } from "@tdesign-react/chat";
import { ArrowUpIcon, AttachIcon } from "tdesign-icons-react";
import { Button, MessagePlugin, Tooltip } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { ChatSenderWithSlots } from "../chat-sender-with-slots";
import { attachmentFileType } from "./conversation-utils";
import styles from "./conversation.module.css";

const Attachments = dynamic(() => import("@tdesign-react/chat").then((module) => module.Attachments), { ssr: false });

const ACCEPTED_FILES = ["image/*", ".pdf", ".doc", ".docx", ".md", ".ppt", ".pptx", ".txt", ".xls", ".xlsx"].join(",");

type DraftFile = {
    file: File;
    id: string;
    previewUrl?: string;
};

type ConversationComposerProps = {
    disabled?: boolean;
    onSend: (input: { files: File[]; prompt: string }) => Promise<void>;
    onStop: () => Promise<void>;
    running: boolean;
    sending: boolean;
    sendingLabel?: string;
};

export function ConversationComposer({
    disabled = false,
    onSend,
    onStop,
    running,
    sending,
    sendingLabel,
}: ConversationComposerProps) {
    const [value, setValue] = useState("");
    const [draftFiles, setDraftFiles] = useState<DraftFile[]>([]);
    const [sendingInput, setSendingInput] = useState(false);
    const [stopping, setStopping] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const senderRef = useRef<HTMLElement>(null);
    const draftFilesRef = useRef(draftFiles);
    const busy = sending || sendingInput || running;
    const canSubmit = value.trim().length > 0 && !busy && !disabled;
    const attachmentItems: TdAttachmentItem[] = draftFiles.map((draft) => ({
        fileType: attachmentFileType(draft.file.type, draft.file.name),
        name: draft.file.name,
        size: draft.file.size,
        url: draft.previewUrl,
    }));

    useEffect(() => {
        draftFilesRef.current = draftFiles;
    }, [draftFiles]);

    useEffect(
        () => () => {
            revokeDraftUrls(draftFilesRef.current);
        },
        [],
    );

    const appendFiles = (files: File[]) => {
        const known = new Set(
            draftFiles.map((draft) => `${draft.file.name}:${draft.file.size}:${draft.file.lastModified}`),
        );
        const added = files.flatMap((file) => {
            const fingerprint = `${file.name}:${file.size}:${file.lastModified}`;

            if (known.has(fingerprint)) {
                return [];
            }

            known.add(fingerprint);

            return [
                {
                    file,
                    id: crypto.randomUUID(),
                    previewUrl: file.type.startsWith("image/") ? URL.createObjectURL(file) : undefined,
                },
            ];
        });

        if (added.length > 0) {
            setDraftFiles([...draftFiles, ...added]);
        }
    };

    const removeFile = (file: DraftFile) => {
        if (file.previewUrl) {
            URL.revokeObjectURL(file.previewUrl);
        }
        setDraftFiles((current) => current.filter((item) => item.id !== file.id));
        requestAnimationFrame(() => senderRef.current?.focus());
    };

    const clearDraftFiles = () => {
        revokeDraftUrls(draftFiles);
        setDraftFiles([]);
    };

    const submit = async (promptValue: string) => {
        const prompt = promptValue.trim();

        if (!prompt || busy || disabled) {
            return;
        }

        setSendingInput(true);
        try {
            await onSend({
                files: draftFiles.map((draft) => draft.file),
                prompt,
            });
            setValue("");
            clearDraftFiles();
        } catch (error) {
            MessagePlugin.error(
                consoleErrorMessage(error, error instanceof Error ? error.message : "消息发送失败，请重试。"),
            );
        } finally {
            setSendingInput(false);
            requestAnimationFrame(() => senderRef.current?.focus());
        }
    };

    const stop = async () => {
        if (stopping) {
            return;
        }

        setStopping(true);
        try {
            await onStop();
        } catch (error) {
            MessagePlugin.error(consoleErrorMessage(error, error instanceof Error ? error.message : "停止运行失败。"));
        } finally {
            setStopping(false);
        }
    };

    const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(event.currentTarget.files ?? []);

        if (files.length > 0) {
            appendFiles(files);
        }

        event.currentTarget.value = "";
    };

    const toolbar = (
        <div className={styles.composerToolbar}>
            <div className={styles.composerToolbarStart}>
                <Tooltip content="上传图片或文档">
                    <Button
                        aria-label="上传附件"
                        className={styles.iconButton}
                        disabled={busy || disabled}
                        icon={<AttachIcon />}
                        onClick={() => fileInputRef.current?.click()}
                        shape="square"
                        variant="text"
                    />
                </Tooltip>
                <span className={styles.composerHint}>支持图片、PDF、Word、表格等文件</span>
            </div>
            <div className={styles.composerToolbarEnd}>
                {running ? (
                    <Button
                        aria-label="停止生成"
                        loading={stopping}
                        onClick={() => void stop()}
                        size="small"
                        theme="default"
                        variant="outline"
                    >
                        停止
                    </Button>
                ) : (
                    <Tooltip content="发送（Enter）">
                        <Button
                            aria-label="发送消息"
                            className={styles.sendButton}
                            disabled={!canSubmit}
                            loading={sendingInput || sending}
                            onClick={() => void submit(value)}
                            shape="circle"
                            theme="primary"
                        >
                            {sendingInput || sending ? undefined : <ArrowUpIcon />}
                        </Button>
                    </Tooltip>
                )}
            </div>
        </div>
    );

    return (
        <section aria-label="会话输入框" className={styles.composerDock}>
            <div className={styles.composerInner}>
                {sendingLabel || running ? (
                    <div className={styles.composerStatus} role="status">
                        {sendingLabel ?? "Pudans AI 正在生成回复..."}
                    </div>
                ) : null}
                <input
                    accept={ACCEPTED_FILES}
                    hidden
                    multiple
                    onChange={handleFileChange}
                    ref={fileInputRef}
                    type="file"
                />
                <div className={styles.composerSurface}>
                    {draftFiles.length > 0 ? (
                        <div className={styles.composerAttachments}>
                            <Attachments
                                imageViewer
                                items={attachmentItems}
                                onFileClick={(event) => {
                                    if (event.detail.url) {
                                        window.open(event.detail.url, "_blank", "noopener,noreferrer");
                                    }
                                }}
                                onRemove={(event) => {
                                    const match = draftFiles.find(
                                        (draft) =>
                                            draft.file.name === event.detail.name &&
                                            draft.file.size === event.detail.size,
                                    );

                                    if (match) {
                                        removeFile(match);
                                    }
                                }}
                                overflow="wrap"
                                removable
                            />
                        </div>
                    ) : null}
                    <ChatSenderWithSlots
                        actions={false}
                        autosize={{ maxRows: 7, minRows: 1 }}
                        className={styles.composerSender}
                        disabled={busy || disabled}
                        onChange={(event) => setValue(event.detail)}
                        onSend={(event) => void submit(event.detail.value)}
                        placeholder={running ? "等待当前回复完成..." : "输入消息，Enter 发送，Shift + Enter 换行"}
                        ref={senderRef}
                        value={value}
                    >
                        <div slot="footer-prefix">{toolbar}</div>
                    </ChatSenderWithSlots>
                </div>
            </div>
        </section>
    );
}

function revokeDraftUrls(files: DraftFile[]) {
    for (const file of files) {
        if (file.previewUrl) {
            URL.revokeObjectURL(file.previewUrl);
        }
    }
}
