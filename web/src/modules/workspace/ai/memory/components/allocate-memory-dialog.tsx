import { useState } from "react";

import { Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { agentHint, agentLabel } from "@/modules/workspace/ai/library/lib/agents";
import type { AiAgentItem } from "@/modules/workspace/ai/library/lib/api";

import type { MemoryAssetView } from "../lib/api";
import { MEMORY_BINDING_LIMIT, memoryAssetTitle } from "../lib/model";

type AllocateMemoryDialogProps = {
  asset: MemoryAssetView;
  ownedAgents: AiAgentItem[];
  pending: boolean;
  onConfirm: (agent: string) => void;
  onClose: () => void;
};

export function AllocateMemoryDialog({
  asset,
  ownedAgents,
  pending,
  onConfirm,
  onClose,
}: AllocateMemoryDialogProps) {
  const [selectedAgent, setSelectedAgent] = useState("");
  const boundAgents = new Set(asset.agents);
  const candidates = ownedAgents.filter(
    (agent) => !boundAgents.has(agent.metadata.name) && agent.metadata.name !== asset.sourceAgentId,
  );
  const limitReached = asset.agents.length >= MEMORY_BINDING_LIMIT;
  const canSubmit = Boolean(selectedAgent) && !limitReached && !pending;

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="分配到 Agent"
      description={`将「${memoryAssetTitle(asset)}」绑定到你的 Agent，最多绑定 ${MEMORY_BINDING_LIMIT} 个 Agent。`}
      size="md"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button type="button" disabled={!canSubmit} onClick={() => onConfirm(selectedAgent)}>
            {pending ? <Loader2 className="animate-spin" /> : null}
            确认分配
          </Button>
        </>
      }
    >
      {limitReached ? (
        <Alert>
          <AlertTitle>已达到绑定上限</AlertTitle>
          <AlertDescription>
            该记忆块已绑定 {asset.agents.length} 个 Agent，请先解绑后再分配。
          </AlertDescription>
        </Alert>
      ) : candidates.length === 0 ? (
        <Alert>
          <AlertTitle>没有可分配的 Agent</AlertTitle>
          <AlertDescription>
            你名下暂无可绑定的 Agent，或所有 Agent 均已绑定该记忆块。
          </AlertDescription>
        </Alert>
      ) : (
        <FormField label="Agent" htmlFor="memory-allocate-agent">
          <Select value={selectedAgent} onValueChange={setSelectedAgent}>
            <SelectTrigger id="memory-allocate-agent" className="w-full">
              <SelectValue placeholder="选择 Agent" />
            </SelectTrigger>
            <SelectContent>
              {candidates.map((agent) => {
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
      )}
    </DialogFormShell>
  );
}
