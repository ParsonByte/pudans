import {
  Boxes,
  Cloud,
  CloudDownload,
  CloudUpload,
  Database,
  FolderArchive,
  HardDrive,
  Server,
} from "lucide-react";

import type { StoragePolicyType } from "../lib/api";

const TYPE_ICONS = {
  local: HardDrive,
  s3: Boxes,
  ks3: Server,
  oss: Cloud,
  cos: Cloud,
  obs: Database,
  qiniu: CloudUpload,
  upyun: CloudDownload,
  onedrive: FolderArchive,
} satisfies Record<StoragePolicyType, typeof Cloud>;

export function StorageTypeIcon({
  type,
  className,
}: {
  type: StoragePolicyType;
  className?: string;
}) {
  const Icon = TYPE_ICONS[type] ?? Cloud;
  return <Icon className={className} />;
}
