import { createFileRoute } from "@tanstack/react-router";

import { LegalView } from "@/modules/auth/legal";
import { LEGAL_KEYS, type LegalDocumentKey } from "@/modules/auth/legal/legal-content";

function normalizeTab(value: unknown): LegalDocumentKey {
  return LEGAL_KEYS.includes(value as LegalDocumentKey) ? (value as LegalDocumentKey) : "terms";
}

export const Route = createFileRoute("/(auth)/legal")({
  validateSearch: (search: Record<string, unknown>) => ({
    tab: normalizeTab(search.tab),
  }),
  component: LegalRoute,
});

function LegalRoute() {
  const { tab } = Route.useSearch();
  const navigate = Route.useNavigate();

  return (
    <LegalView
      activeTab={tab}
      onTabChange={(nextTab) => {
        void navigate({ search: { tab: nextTab } });
      }}
    />
  );
}
