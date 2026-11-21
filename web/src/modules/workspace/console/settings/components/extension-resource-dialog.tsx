"use client";

import type { ExtensionRecord } from "../lib/extension-records";
import {
  type ExtensionResourceKind,
  buildExtensionResourceRecord,
  createExtensionResource,
  extensionResourceDefinition,
  extensionResourceFields,
  resolveExtensionResourceError,
  updateExtensionResource,
  validateExtensionResourceValues,
} from "../lib/extension-resources";
import { ExtensionRecordDialog } from "./extension-record-dialog";
import { SecretResourceDialog } from "./secret-resource-dialog";

type ExtensionResourceDialogProps = {
  kind: ExtensionResourceKind;
  record?: ExtensionRecord;
  onClose: () => void;
};

export function ExtensionResourceDialog({ kind, record, onClose }: ExtensionResourceDialogProps) {
  if (kind === "secrets") {
    return <SecretResourceDialog record={record} onClose={onClose} />;
  }

  return <ResourceRecordDialog kind={kind} record={record} onClose={onClose} />;
}

type ResourceRecordDialogProps = {
  kind: Exclude<ExtensionResourceKind, "secrets">;
  record?: ExtensionRecord;
  onClose: () => void;
};

function ResourceRecordDialog({ kind, record, onClose }: ResourceRecordDialogProps) {
  const definition = extensionResourceDefinition(kind);
  const fields = extensionResourceFields(kind, Boolean(record));

  return (
    <ExtensionRecordDialog
      title={`${record ? "编辑" : "新建"}${definition.label}`}
      description={definition.dialogDescription}
      fields={fields}
      existing={record}
      createRecord={(body) => buildExtensionResourceRecord(kind, record, body)}
      saveRecord={(next) =>
        record
          ? updateExtensionResource(kind, record.metadata.name, next)
          : createExtensionResource(kind, next)
      }
      invalidateKeys={[["extension-resources"]]}
      successMessage={record ? "扩展资源已保存。" : "扩展资源已创建。"}
      resolveError={resolveExtensionResourceError}
      validateValues={(values) => validateExtensionResourceValues(kind, values)}
      onClose={onClose}
    />
  );
}
