"use client";

import { useRef, useState } from "react";

import { FileImageIcon, UploadIcon } from "tdesign-icons-react";
import { Button, Image, ImageViewer, Input } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { type SiteAssetField, uploadSiteAsset } from "../composables/_site-assets";

type SiteImageFieldProps = {
    disabled?: boolean;
    field: SiteAssetField;
    onChange?: (value: string) => void;
    readOnly?: boolean;
    value?: string;
};

const MAX_SIZE = 2 * 1024 * 1024;
const ACCEPT = ".ico,.jpeg,.jpg,.png,.svg,.webp";

export function SiteImageField({ disabled, field, onChange, readOnly, value }: SiteImageFieldProps) {
    const token = useDesignTokens();
    const prompt = useMessagePrompt();
    const inputRef = useRef<HTMLInputElement>(null);
    const [failedUrl, setFailedUrl] = useState<string>();
    const [uploading, setUploading] = useState(false);
    const imageUrl = value?.trim();
    const showImage = Boolean(imageUrl) && failedUrl !== imageUrl;
    const blocked = disabled || readOnly;

    async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) {
            return;
        }
        if (file.size > MAX_SIZE) {
            prompt.error({ content: "图片大小不能超过 2 MB。" });
            return;
        }
        setUploading(true);
        try {
            const url = await uploadSiteAsset(field, file);
            onChange?.(url);
            setFailedUrl(undefined);
            prompt.success({ content: "图片已上传，保存后生效。" });
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "图片上传失败，请稍后重试。") });
        } finally {
            setUploading(false);
        }
    }

    return (
        <div className="flex min-w-0 items-start gap-3">
            <div
                className="flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-lg"
                style={{ background: token.colorFillQuaternary }}
            >
                {showImage && imageUrl ? (
                    <ImageViewer
                        images={[imageUrl]}
                        trigger={({ open }) => (
                            <Image
                                alt="站点图片"
                                className="size-full cursor-zoom-in"
                                fit="contain"
                                onClick={() => open()}
                                onError={() => setFailedUrl(imageUrl)}
                                src={imageUrl}
                            />
                        )}
                    />
                ) : (
                    <FileImageIcon size={26} style={{ color: token.colorTextTertiary }} />
                )}
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                    <Button
                        disabled={blocked || uploading}
                        icon={<UploadIcon />}
                        loading={uploading}
                        onClick={() => inputRef.current?.click()}
                        size="small"
                        variant="outline"
                    >
                        {showImage ? "替换图片" : "导入图片"}
                    </Button>
                    {showImage ? (
                        <Button
                            disabled={blocked || uploading}
                            onClick={() => {
                                onChange?.("");
                                setFailedUrl(undefined);
                            }}
                            size="small"
                            variant="text"
                        >
                            移除
                        </Button>
                    ) : null}
                </div>
                <Input
                    disabled={blocked}
                    onChange={(nextValue) => onChange?.(String(nextValue))}
                    placeholder="也可填写图片地址"
                    size="small"
                    value={value ?? ""}
                />
                <input
                    ref={inputRef}
                    accept={ACCEPT}
                    className="hidden"
                    onChange={(event) => void handleFileChange(event)}
                    type="file"
                />
            </div>
        </div>
    );
}

SiteImageField.displayName = "SiteImageField";
