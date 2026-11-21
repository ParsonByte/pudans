"use client";

import { AiUsageRequestsView } from "@/modules/workspace/ai/usage/requests";

export default function MyUsageRequestsPage() {
  return <AiUsageRequestsView scopeLock="self" />;
}
