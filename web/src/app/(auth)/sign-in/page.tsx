"use client";

import { Suspense } from "react";

import { useSearchParams } from "next/navigation";

import { SignInView } from "@/modules/auth/sign-in";

function SignInContent() {
  const searchParams = useSearchParams();

  return (
    <SignInView
      redirectTo={searchParams.get("redirect") ?? undefined}
      errorCode={searchParams.get("error") ?? undefined}
      errorDetail={searchParams.get("detail") ?? undefined}
    />
  );
}

export default function SignInPage() {
  return (
    <Suspense fallback={null}>
      <SignInContent />
    </Suspense>
  );
}
