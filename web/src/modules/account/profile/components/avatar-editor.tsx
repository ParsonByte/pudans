"use client";

import { useRef, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Loader2, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { resolveApiErrorWithDetail } from "@/lib/api-error";

const AVATAR_TYPES = new Set(["image/png", "image/jpeg", "image/jpg", "image/webp"]);

const ERROR_MESSAGES = {
  notFound: "用户不存在。",
  conflict: "头像更新失败，请稍后重试。",
};

type AvatarEditorProps = {
  avatar?: string;
  displayName?: string;
  onChanged: () => void;
};

export function AvatarEditor({ avatar, displayName, onChanged }: AvatarEditorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [confirmRemoveOpen, setConfirmRemoveOpen] = useState(false);

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => (await apiClient.user.uploadMyAvatar({ file })).data,
    onSuccess: () => {
      toast.success("头像已更新。");
      onChanged();
    },
    onError: (error) => toast.error(resolveApiErrorWithDetail(error, ERROR_MESSAGES)),
    onSettled: () => {
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    },
  });

  const removeMutation = useMutation({
    mutationFn: async () => (await apiClient.user.deleteMyAvatar()).data,
    onSuccess: () => {
      toast.success("头像已移除。");
      setConfirmRemoveOpen(false);
      onChanged();
    },
    onError: (error) => {
      setConfirmRemoveOpen(false);
      toast.error(resolveApiErrorWithDetail(error, ERROR_MESSAGES));
    },
  });

  const initials = (displayName || "?").slice(0, 2).toUpperCase();

  return (
    <div className="flex flex-wrap items-center gap-4">
      <Avatar className="size-16">
        {avatar ? <AvatarImage src={avatar} alt="" /> : null}
        <AvatarFallback className="text-lg">{initials}</AvatarFallback>
      </Avatar>
      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/png,image/jpeg,image/jpg,image/webp"
          className="sr-only"
          aria-label="选择头像图片"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (!file) {
              return;
            }
            if (!AVATAR_TYPES.has(file.type)) {
              toast.error("头像仅支持 PNG、JPEG 或 WebP 图片。");
              event.target.value = "";
              return;
            }
            uploadMutation.mutate(file);
          }}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={uploadMutation.isPending}
          onClick={() => fileInputRef.current?.click()}
        >
          {uploadMutation.isPending ? <Loader2 className="animate-spin" /> : <Upload />}
          上传头像
        </Button>
        {avatar ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={removeMutation.isPending}
            onClick={() => setConfirmRemoveOpen(true)}
          >
            <Trash2 />
            移除头像
          </Button>
        ) : null}
      </div>
      <ConfirmDialog
        open={confirmRemoveOpen}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmRemoveOpen(false);
          }
        }}
        title="移除头像"
        desc="确定移除当前头像？移除后将显示由姓名生成的首字母头像。"
        destructive
        confirmText="移除"
        isLoading={removeMutation.isPending}
        handleConfirm={() => removeMutation.mutate()}
      />
    </div>
  );
}
