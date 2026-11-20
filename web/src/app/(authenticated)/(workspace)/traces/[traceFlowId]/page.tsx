"use client";

import { useParams } from "next/navigation";

import { TraceDetailView } from "@/modules/workspace/console/traces/detail";

export default function TraceDetailPage() {
  const params = useParams<{ traceFlowId: string }>();

  return <TraceDetailView traceFlowId={params.traceFlowId} />;
}
