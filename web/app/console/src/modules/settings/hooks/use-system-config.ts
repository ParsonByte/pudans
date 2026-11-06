import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useToast } from "@pudans/components";
import { apiClient } from "@pudans/api-client";

const SYSTEM_CONFIG_BASE = "/apis/console.api.pudans.tolim.cn/v1alpha1/systemconfigs";

export type SystemConfig = Record<string, unknown>;

export function useSystemConfig(group: string) {
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setConfig(null);
    setFailed(false);
    setLoading(true);
    apiClient.http
      .get<SystemConfig>(SYSTEM_CONFIG_BASE + "/" + group, {
        signal: controller.signal,
        pudansRetry: false as const,
      })
      .then((data) => {
        if (!active) {
          return;
        }
        setConfig(data ?? {});
        setLoading(false);
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setFailed(true);
        setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [group]);

  return { config, setConfig, failed, loading };
}

export interface ConfigFormOptions<T extends object> {
  group: string;
  parse: (config: SystemConfig) => T;
  merge: (config: SystemConfig, values: T) => SystemConfig;
}

export function useConfigForm<T extends object>({ group, parse, merge }: ConfigFormOptions<T>) {
  const { t } = useTranslation("console");
  const toast = useToast();
  const { config, setConfig, failed, loading } = useSystemConfig(group);
  const [initial, setInitial] = useState<T | null>(null);
  const [values, setValues] = useState<T | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (config === null) {
      return;
    }
    const parsed = parse(config);
    setInitial(parsed);
    setValues(parsed);
  }, [config, parse]);

  const save = useCallback(async (): Promise<boolean> => {
    if (config === null || values === null) {
      return false;
    }
    const payload = merge(config, values);
    setSaving(true);
    try {
      await apiClient.http.put(SYSTEM_CONFIG_BASE + "/" + group, payload);
      setConfig(payload);
      setInitial(values);
      toast.success(t("settingsSaved"));
      return true;
    } catch {
      toast.error(t("settingsSaveFailed"));
      return false;
    } finally {
      setSaving(false);
    }
  }, [config, values, merge, group, setConfig, toast, t]);

  const reset = useCallback(() => {
    if (initial !== null) {
      setValues(initial);
    }
  }, [initial]);

  return {
    config,
    initial,
    values,
    setValues,
    saving,
    save,
    reset,
    failed,
    loading,
  };
}
