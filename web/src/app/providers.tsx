"use client";

import { type ReactNode, useState } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { MotionConfig } from "motion/react";
import { ThemeProvider } from "next-themes";

import { MotionBridge } from "@/components/motion/motion-bridge";
import { SudoProvider } from "@/components/sudo/sudo-provider";
import { Toaster } from "@/components/ui/sonner";
import { DirectionProvider } from "@/context/direction-provider";
import { installApiInterceptors } from "@/lib/api-interceptor";

installApiInterceptors();

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      storageKey="pudans-ui-theme"
      disableTransitionOnChange
    >
      <DirectionProvider>
        <QueryClientProvider client={queryClient}>
          <MotionConfig reducedMotion="user">
            <MotionBridge />
            <SudoProvider>{children}</SudoProvider>
          </MotionConfig>
          <Toaster position="top-center" richColors />
          <ReactQueryDevtools initialIsOpen={false} />
        </QueryClientProvider>
      </DirectionProvider>
    </ThemeProvider>
  );
}
