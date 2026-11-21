"use client";

import { AiUsageSessionsView } from "@/modules/workspace/ai/usage/sessions";

export default function MyUsageSessionsPage() {
  return <AiUsageSessionsView scopeLock="self" />;
}
