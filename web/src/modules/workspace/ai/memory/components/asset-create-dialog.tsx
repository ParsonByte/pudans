import { useState } from "react";

import { Loader2 } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>创建记忆资产</DialogTitle>
          <DialogDescription>
            以你的 Agent 记忆作用域创建记忆块，当前团队：{team}。
          </DialogDescription>
        </DialogHeader>

        {ownedAgents.length === 0 ? (
          <Alert>
            <AlertTitle>没有可用的 Agent</AlertTitle>
            <AlertDescription>
              创建记忆资产需要选择一个你名下的 Agent 作为来源，请先创建 Agent。
            </AlertDescription>
          </Alert>
        ) : null}

        <div className="space-y-2">
          <Label htmlFor="memory-asset-name">名称</Label>
          <Input
            id="memory-asset-name"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="例如：客户成功助手记忆"
            disabled={pending}
            maxLength={80}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="memory-asset-description">描述（可选）</Label>
          <Textarea
            id="memory-asset-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="说明该记忆块的用途"
            className="min-h-20"
            disabled={pending}
            maxLength={200}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="memory-asset-agent">来源 Agent</Label>
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
        </div>

        <div className="space-y-2">
          <Label htmlFor="memory-asset-visibility">可见性</Label>
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
        </div>

        <DialogFooter>
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
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
