"use client";

import { Suspense } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import { LegalView } from "@/modules/auth/legal";
import { LEGAL_KEYS, type LegalDocumentKey } from "@/modules/auth/legal/legal-content";

function normalizeTab(value: string | null): LegalDocumentKey {
  return LEGAL_KEYS.includes(value as LegalDocumentKey) ? (value as LegalDocumentKey) : "terms";
}

function LegalContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tab = normalizeTab(searchParams.get("tab"));

  return (
    <LegalView
      activeTab={tab}
      onTabChange={(nextTab) => {
        router.push(`/legal?tab=${nextTab}`);
      }}
    />
  );
}

export default function LegalPage() {
  return (
    <Suspense fallback={null}>
      <LegalContent />
    </Suspense>
  );
}
