"use client";

import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { HelpCircleIcon } from "tdesign-icons-react";
import { Form, Select, Tooltip } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { type LiveRecordingRetryMode, type LiveRecordingView, retryLiveRecording } from "../../../../composables/_live";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import { LIVE_RECORDING_QUERY_ROOT, liveRecordingDisplayName } from "../composables/_live-recordings";

type LiveRecordingRetryDialogProps = {
    onClose: () => void;
    recording?: LiveRecordingView;
};

const RETRY_MODE_OPTIONS = [
    { label: "重新上传本地文件", value: "UPLOAD" },
    { label: "按原直播源重新录制", value: "STREAM" },
];

const RETRY_MODE_HINT = "重新上传使用已落盘的分段，重新拉流会按原直播源开一场新录制。";

export function LiveRecordingRetryDialog({ onClose, recording }: LiveRecordingRetryDialogProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [mode, setMode] = useState<LiveRecordingRetryMode>((recording?.localBytes ?? 0) > 0 ? "UPLOAD" : "STREAM");
    const retryMutation = useMutation({
        mutationFn: async (payload: { mode: LiveRecordingRetryMode; name: string }) =>
            retryLiveRecording(payload.name, payload.mode),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "已提交重试。" });
            void queryClient.invalidateQueries({ queryKey: LIVE_RECORDING_QUERY_ROOT });
            onClose();
        },
    });

    if (!recording) {
        return null;
    }

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={onClose}
                    onSubmit={() => retryMutation.mutate({ mode, name: recording.name })}
                    pending={retryMutation.isPending}
                    submitLabel="重试"
                />
            }
            onClose={onClose}
            open
            title={`重试录制 · ${liveRecordingDisplayName(recording)}`}
            width={560}
        >
            <Form form={form} labelAlign="top" layout="vertical">
                <Form.FormItem
                    label={
                        <span className="flex items-center gap-1">
                            重试方式
                            <Tooltip content={RETRY_MODE_HINT} placement="top-left">
                                <HelpCircleIcon />
                            </Tooltip>
                        </span>
                    }
                    name="mode"
                >
                    <Select
                        onChange={(value) => setMode(String(value) as LiveRecordingRetryMode)}
                        options={RETRY_MODE_OPTIONS}
                        value={mode}
                        valueType="value"
                    />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
