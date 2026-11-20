import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

type UsageRefreshToggleProps = {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
};

export function UsageRefreshToggle({
  checked,
  onCheckedChange,
  disabled = false,
}: UsageRefreshToggleProps) {
  return (
    <div className="flex items-center gap-2">
      <Switch
        id="usage-auto-refresh"
        size="sm"
        checked={checked}
        onCheckedChange={onCheckedChange}
        disabled={disabled}
      />
      <Label htmlFor="usage-auto-refresh" className="text-xs text-muted-foreground">
        自动刷新（5 秒）
      </Label>
    </div>
  );
}
