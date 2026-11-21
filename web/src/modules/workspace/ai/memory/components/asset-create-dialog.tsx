import { useState } from "react";

import { Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { agentHint, agentLabel } from "@/modules/workspace/ai/library/lib/agents";
import type { AiAgentItem } from "@/modules/workspace/ai/library/lib/api";

import type { MemoryAssetCreateInput, MemoryAssetVisibility } from "../lib/api";
import { MEMORY_VISIBILITY_LABELS } from "../lib/model";

type AssetCreateDialogProps = {
  team: string;
  currentUserId?: string;
  ownedAgents: AiAgentItem[];
  pending: boolean;
  onCreate: (input: MemoryAssetCreateInput) => void;
  onClose: () => void;
};

export function AssetCreateDialog({
  team,
  currentUserId,
  ownedAgents,
  pending,
  onCreate,
  onClose,
}: AssetCreateDialogProps) {
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [sourceAgentId, setSourceAgentId] = useState("");
  const [visibility, setVisibility] = useState<MemoryAssetVisibility>("TEAM");

  const canSubmit =
    displayName.trim().length > 0 && Boolean(sourceAgentId) && Boolean(currentUserId) && !pending;

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="创建记忆资产"
      description={`以你的 Agent 记忆作用域创建记忆块，当前团队：${team}。`}
      size="md"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!canSubmit}
            onClick={() =>
              onCreate({
                displayName: displayName.trim(),
                description: description.trim() || undefined,
                team,
                ownerUserId: currentUserId as string,
                sourceUserId: currentUserId as string,
                sourceAgentId,
                visibility,
              })
            }
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            创建
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {ownedAgents.length === 0 ? (
          <Alert>
            <AlertTitle>没有可用的 Agent</AlertTitle>
            <AlertDescription>
              创建记忆资产需要选择一个你名下的 Agent 作为来源，请先创建 Agent。
            </AlertDescription>
          </Alert>
        ) : null}

        <FormField label="名称" htmlFor="memory-asset-name">
          <Input
            id="memory-asset-name"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="例如：客户成功助手记忆"
            disabled={pending}
            maxLength={80}
          />
        </FormField>

        <FormField label="描述（可选）" htmlFor="memory-asset-description">
          <Textarea
            id="memory-asset-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="说明该记忆块的用途"
            className="min-h-20"
            disabled={pending}
            maxLength={200}
          />
        </FormField>

        <FormField label="来源 Agent" htmlFor="memory-asset-agent">
          <Select
            value={sourceAgentId}
            onValueChange={setSourceAgentId}
            disabled={pending || ownedAgents.length === 0}
          >
            <SelectTrigger id="memory-asset-agent" className="w-full">
              <SelectValue placeholder="选择来源 Agent" />
            </SelectTrigger>
            <SelectContent>
              {ownedAgents.map((agent) => {
                const hint = agentHint(agent);
                return (
                  <SelectItem key={agent.metadata.name} value={agent.metadata.name}>
                    {agentLabel(agent)}
                    {hint ? ` · ${hint}` : ""}
                  </SelectItem>
                );
              })}
            </SelectContent>
          </Select>
        </FormField>

        <FormField label="可见性" htmlFor="memory-asset-visibility">
          <Select
            value={visibility}
            onValueChange={(value) => setVisibility(value as MemoryAssetVisibility)}
            disabled={pending}
          >
            <SelectTrigger id="memory-asset-visibility" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="TEAM">{MEMORY_VISIBILITY_LABELS.TEAM}</SelectItem>
              <SelectItem value="PRIVATE">{MEMORY_VISIBILITY_LABELS.PRIVATE}</SelectItem>
            </SelectContent>
          </Select>
        </FormField>
      </div>
    </DialogFormShell>
  );
}
