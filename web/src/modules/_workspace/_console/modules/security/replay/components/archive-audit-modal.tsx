"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { DatePicker, Form } from "tdesign-react";
import type { FormRule } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount } from "../../../../composables/_format";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";

type ArchiveAuditModalProps = {
    onClose: () => void;
};

const ARCHIVE_RULES: FormRule[] = [{ message: "请选择归档截止时间。", required: true }];

function archiveCutoff(value?: string | number | Date): string | undefined {
    if (!value) {
        return undefined;
    }

    const date = value instanceof Date ? value : new Date(value);

    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

export function ArchiveAuditModal({ onClose }: ArchiveAuditModalProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const archive = useMutation({
        mutationFn: async (before: string) =>
            (await apiClient.auditLog.archiveAuditLogs({ auditArchiveRequest: { before } })).data,
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: async (result) => {
            prompt.success({ content: `已归档 ${formatCount(result.archivedCount)} 条审计记录。` });
            onClose();
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ["console", "replay"] }),
                queryClient.invalidateQueries({ queryKey: ["console", "audit"] }),
                queryClient.invalidateQueries({ queryKey: ["console", "traces"] }),
            ]);
        },
    });
    const submit = async () => {
        const validation = await form.validate({ showErrorMessage: true });
        if (validation !== true) {
            return;
        }

        const before = archiveCutoff(form.getFieldValue("before"));
        if (!before) {
            return;
        }

        await archive.mutateAsync(before).catch(() => undefined);
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={onClose}
                    onSubmit={() => void submit()}
                    pending={archive.isPending}
                    submitLabel="开始归档"
                />
            }
            onClose={onClose}
            open
            title="归档审计"
            width={520}
        >
            <Form form={form} labelAlign="top" layout="vertical">
                <Form.FormItem label="归档截止时间" name="before" rules={ARCHIVE_RULES}>
                    <DatePicker
                        disableDate={(value) => new Date(value).valueOf() > Date.now()}
                        enableTimePicker
                        format="YYYY-MM-DD HH:mm:ss"
                        placeholder="选择日期与时间"
                        style={{ width: "100%" }}
                        valueType="YYYY-MM-DD HH:mm:ss"
                    />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
