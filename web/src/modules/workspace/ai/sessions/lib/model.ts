export const DEFAULT_SESSION_PAGE_SIZE = 20;

const SESSION_STATUS_ACTIVE = "ACTIVE";

const SESSION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "活跃",
  DELETED: "已删除",
};

export function sessionStatusLabel(status?: string | null): string {
  if (!status) {
    return "—";
  }
  return SESSION_STATUS_LABELS[status] ?? status;
}

export function sessionStatusBadgeVariant(status?: string | null): "secondary" | "outline" {
  return status === SESSION_STATUS_ACTIVE ? "secondary" : "outline";
}
