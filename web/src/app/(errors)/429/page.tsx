"use client";

import { Suspense } from "react";

import { useSearchParams } from "next/navigation";

import { TooManyRequestsError } from "@/modules/errors";

function TooManyRequestsContent() {
  const searchParams = useSearchParams();

  return <TooManyRequestsError description={searchParams.get("detail") ?? undefined} />;
}

export default function TooManyRequestsPage() {
  return (
    <Suspense fallback={null}>
      <TooManyRequestsContent />
    </Suspense>
  );
}
