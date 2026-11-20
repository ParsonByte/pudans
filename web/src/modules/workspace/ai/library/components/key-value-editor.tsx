import { Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type { KeyValueEntry } from "../lib/model";

type KeyValueEditorProps = {
  value: readonly KeyValueEntry[];
  onChange: (value: KeyValueEntry[]) => void;
  keyPlaceholder: string;
  valuePlaceholder: string;
  addLabel: string;
  disabled?: boolean;
};

export function KeyValueEditor({
  value,
  onChange,
  keyPlaceholder,
  valuePlaceholder,
  addLabel,
  disabled = false,
}: KeyValueEditorProps) {
  const updateEntry = (index: number, patch: Partial<KeyValueEntry>) => {
    onChange(value.map((entry, current) => (current === index ? { ...entry, ...patch } : entry)));
  };

  return (
    <div className="space-y-2">
      {value.map((entry, index) => (
        <div key={index} className="flex items-start gap-2">
          <Input
            value={entry.key}
            onChange={(event) => updateEntry(index, { key: event.target.value })}
            placeholder={keyPlaceholder}
            className="font-mono"
            disabled={disabled}
            aria-label={`键 ${index + 1}`}
          />
          <Input
            value={entry.value}
            onChange={(event) => updateEntry(index, { value: event.target.value })}
            placeholder={valuePlaceholder}
            className="font-mono"
            disabled={disabled}
            aria-label={`值 ${index + 1}`}
          />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="删除该行"
            disabled={disabled}
            onClick={() => onChange(value.filter((_, current) => current !== index))}
          >
            <Trash2 />
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={disabled}
        onClick={() => onChange([...value, { key: "", value: "" }])}
      >
        <Plus />
        {addLabel}
      </Button>
    </div>
  );
}
