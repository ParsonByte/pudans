import { useMemo, useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { FileJson, Upload } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

import {
  AI_API_VERSION,
  AI_MODEL_KIND,
  AI_PROVIDER_ACCOUNT_KIND,
  AI_PROVIDER_KIND,
  createModel,
  createProvider,
  createProviderAccount,
  resolveAiProviderApiError,
} from "../lib/api";
import { type ProviderBundle, accountHasSecret, parseProviderBundle } from "../lib/bundle";
import { ProviderDialogSection, ProviderDialogShell } from "./provider-dialog-shell";

type ProviderImportDialogProps = {
  existingProviderNames: ReadonlySet<string>;
  existingAccountNames: ReadonlySet<string>;
  existingModelNames: ReadonlySet<string>;
  onImported: () => Promise<void>;
  onClose: () => void;
};

type ImportPreview = {
  bundle?: ProviderBundle;
  error?: string;
  importableProviders: number;
  skippedProviders: number;
  accounts: number;
  models: number;
  accountsWithSecret: number;
  accountsWithoutSecret: number;
};

type ImportSummary = {
  providers: number;
  accounts: number;
  models: number;
  skippedProviders: number;
  skippedAccounts: number;
  skippedModels: number;
  accountsWithoutSecret: number;
  failures: string[];
};

export function ProviderImportDialog({
  existingProviderNames,
  existingAccountNames,
  existingModelNames,
  onImported,
  onClose,
}: ProviderImportDialogProps) {
  const [raw, setRaw] = useState("");

  const preview = useMemo<ImportPreview>(() => {
    if (!raw.trim()) {
      return {
        importableProviders: 0,
        skippedProviders: 0,
        accounts: 0,
        models: 0,
        accountsWithSecret: 0,
        accountsWithoutSecret: 0,
      };
    }
    try {
      const bundle = parseProviderBundle(raw);
      const importable = bundle.providers.filter(
        (entry) => !existingProviderNames.has(entry.provider.metadata.name),
      );
      const accounts = bundle.providers.flatMap((entry) => entry.accounts);
      const accountsWithSecret = accounts.filter((account) => accountHasSecret(account)).length;
      return {
        bundle,
        importableProviders: importable.length,
        skippedProviders: bundle.providers.length - importable.length,
        accounts: accounts.length,
        models: bundle.providers.reduce((total, entry) => total + entry.models.length, 0),
        accountsWithSecret,
        accountsWithoutSecret: accounts.length - accountsWithSecret,
      };
    } catch (error) {
      return {
        error: error instanceof Error ? error.message : "无法读取导入文件。",
        importableProviders: 0,
        skippedProviders: 0,
        accounts: 0,
        models: 0,
        accountsWithSecret: 0,
        accountsWithoutSecret: 0,
      };
    }
  }, [existingProviderNames, raw]);

  const mutation = useMutation({
    mutationFn: async (bundle: ProviderBundle): Promise<ImportSummary> => {
      const summary: ImportSummary = {
        providers: 0,
        accounts: 0,
        models: 0,
        skippedProviders: 0,
        skippedAccounts: 0,
        skippedModels: 0,
        accountsWithoutSecret: 0,
        failures: [],
      };
      for (const entry of bundle.providers) {
        const providerName = entry.provider.metadata.name;
        if (existingProviderNames.has(providerName)) {
          summary.skippedProviders += 1;
          continue;
        }
        try {
          await createProvider({
            ...entry.provider,
            apiVersion: entry.provider.apiVersion || AI_API_VERSION,
            kind: entry.provider.kind || AI_PROVIDER_KIND,
          });
          summary.providers += 1;
        } catch (error) {
          summary.failures.push(`供应商 ${providerName}：${resolveAiProviderApiError(error)}`);
          continue;
        }
        for (const account of entry.accounts) {
          const accountName = account.metadata.name;
          if (existingAccountNames.has(accountName)) {
            summary.skippedAccounts += 1;
            continue;
          }
          if (!accountHasSecret(account)) {
            summary.accountsWithoutSecret += 1;
          }
          try {
            await createProviderAccount({
              ...account,
              apiVersion: account.apiVersion || AI_API_VERSION,
              kind: account.kind || AI_PROVIDER_ACCOUNT_KIND,
              spec: { ...account.spec, provider: providerName },
            });
            summary.accounts += 1;
          } catch (error) {
            summary.failures.push(`账号 ${accountName}：${resolveAiProviderApiError(error)}`);
          }
        }
        for (const model of entry.models) {
          const modelName = model.metadata.name;
          if (existingModelNames.has(modelName)) {
            summary.skippedModels += 1;
            continue;
          }
          try {
            await createModel({
              ...model,
              apiVersion: model.apiVersion || AI_API_VERSION,
              kind: model.kind || AI_MODEL_KIND,
              spec: { ...model.spec, provider: providerName },
            });
            summary.models += 1;
          } catch (error) {
            summary.failures.push(`模型 ${modelName}：${resolveAiProviderApiError(error)}`);
          }
        }
      }
      return summary;
    },
    onSuccess: async (summary) => {
      await onImported();
      const created = [
        `${summary.providers} 个供应商`,
        `${summary.accounts} 个账号`,
        `${summary.models} 个模型`,
      ].join("、");
      toast.success(`导入完成：新增 ${created}。`);
      const skipped = summary.skippedProviders + summary.skippedAccounts + summary.skippedModels;
      if (skipped > 0) {
        toast.info(`已跳过 ${skipped} 个同名资源，未做覆盖。`);
      }
      if (summary.accountsWithoutSecret > 0) {
        toast.info(
          `${summary.accountsWithoutSecret} 个账号未包含密钥，请到供应商编辑器补齐后再测试。`,
        );
      }
      if (summary.failures.length > 0) {
        const extra =
          summary.failures.length > 1 ? `（另有 ${summary.failures.length - 1} 项）` : "";
        toast.error(`部分资源导入失败：${summary.failures[0]}${extra}`);
      }
      onClose();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!preview.bundle) {
      toast.error(preview.error ?? "请先粘贴或选择 JSON 文件。");
      return;
    }
    if (preview.importableProviders === 0) {
      toast.error("文件中的所有供应商都已存在。");
      return;
    }
    mutation.mutate(preview.bundle);
  };

  const pickFile = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    try {
      setRaw(await file.text());
    } catch {
      toast.error("无法读取所选文件。");
    }
  };

  return (
    <ProviderDialogShell
      title="导入供应商"
      description="导入供应商、账号与模型的 JSON。导出文件默认不包含密钥，可在编辑器中补齐。"
      icon={<FileJson className="size-4" />}
      onSubmit={submit}
      onClose={onClose}
      isPending={mutation.isPending}
      submitLabel="导入"
      className="sm:max-w-3xl"
    >
      <ProviderDialogSection title="JSON 文件">
        <div className="flex flex-wrap items-center gap-2">
          <Input
            type="file"
            accept="application/json,.json"
            aria-label="选择 JSON 文件"
            onChange={(event) => {
              void pickFile(event.target.files?.[0]);
              event.target.value = "";
            }}
            className="max-w-72"
          />
          <span className="text-xs text-muted-foreground">也可以直接粘贴到下方输入框。</span>
        </div>
        <Textarea
          value={raw}
          onChange={(event) => setRaw(event.target.value)}
          rows={10}
          placeholder='{"format":"pudans-ai-provider-bundle","version":1,"providers":[...]}'
          className="font-mono text-xs"
          aria-label="供应商 JSON"
        />
        {preview.error ? <p className="text-xs text-destructive">{preview.error}</p> : null}
      </ProviderDialogSection>

      <ProviderDialogSection title="预览">
        {!raw.trim() ? (
          <div className="flex min-h-20 items-center justify-center border border-dashed px-4 py-5 text-center text-xs text-muted-foreground">
            粘贴或选择 JSON 后，这里会显示可导入的资源数量。
          </div>
        ) : preview.error ? null : (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary">{preview.importableProviders} 个供应商</Badge>
              <Badge variant="secondary">{preview.accounts} 个账号</Badge>
              <Badge variant="secondary">{preview.models} 个模型</Badge>
              {preview.skippedProviders > 0 ? (
                <Badge variant="outline">{preview.skippedProviders} 个已存在将跳过</Badge>
              ) : null}
            </div>
            {preview.accountsWithoutSecret > 0 ? (
              <p className="text-xs text-muted-foreground">
                其中 {preview.accountsWithoutSecret} 个账号不含密钥；导入后需补齐密钥才能调用。
              </p>
            ) : preview.accounts > 0 ? (
              <p className="text-xs text-muted-foreground">
                文件包含 {preview.accountsWithSecret} 个带密钥的账号，导入时会一并写入。
              </p>
            ) : null}
            <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
              <Upload className="size-3" />
              同名供应商、账号或模型不会覆盖，只会跳过。
            </p>
          </div>
        )}
      </ProviderDialogSection>
    </ProviderDialogShell>
  );
}
