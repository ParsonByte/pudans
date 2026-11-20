"use client";

import { Suspense } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import { ConsoleSettingsView } from "@/modules/workspace/console/settings";
import { normalizeParameterGroupKey } from "@/modules/workspace/console/settings/lib/parameter-fields";

function SettingsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tab = normalizeParameterGroupKey(searchParams.get("tab") ?? undefined);

  return (
    <ConsoleSettingsView
      activeTab={tab}
      onTabChange={(nextTab) => {
        router.push(`/settings?tab=${nextTab}`);
      }}
    />
  );
}

export default function SettingsPage() {
  return (
    <Suspense fallback={null}>
      <SettingsContent />
    </Suspense>
  );
}
