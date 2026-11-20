"use client";

import { useParams } from "next/navigation";

import { JourneyDetailView } from "@/modules/workspace/console/traces/journey-detail";

export default function JourneyDetailPage() {
  const params = useParams<{ uid: string }>();

  return <JourneyDetailView uid={params.uid} />;
}
