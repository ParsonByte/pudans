"use client";

import { ExtensionRecordDialog } from "@/modules/workspace/console/settings/components/extension-record-dialog";
import type { ExtensionRecord } from "@/modules/workspace/console/settings/lib/extension-records";

import {
  buildAuthProviderRecord,
  createAuthProviderRecord,
  resolveAuthProviderError,
  updateAuthProviderRecord,
} from "../lib/auth-provider-api";
import { authProviderFields } from "../lib/authentication-fields";

type AuthProviderDialogProps = {
  record?: ExtensionRecord;
  onClose: () => void;
};

export function AuthProviderDialog({ record, onClose }: AuthProviderDialogProps) {
  return (
    <ExtensionRecordDialog
      title={`${record ? "编辑" : "新建"}登录提供方`}
      description="登录提供方来自 authproviders 扩展资源；启用状态通过 auth-providers 接口单独维护。"
      fields={authProviderFields(Boolean(record))}
      existing={record}
      createRecord={(body) => buildAuthProviderRecord(record, body)}
      saveRecord={(next) =>
        record
          ? updateAuthProviderRecord(record.metadata.name, next)
          : createAuthProviderRecord(next)
      }
      invalidateKeys={[["auth-provider-resources"], ["auth-provider-states"]]}
      successMessage={record ? "登录提供方已保存。" : "登录提供方已创建。"}
      resolveError={resolveAuthProviderError}
      onClose={onClose}
    />
  );
}
