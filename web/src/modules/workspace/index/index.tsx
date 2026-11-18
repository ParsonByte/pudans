import { Sparkles } from "lucide-react";

import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";

import { ChatComposer } from "./components/chat-composer";

export function ChatNewView() {
  return (
    <div className="flex h-svh min-h-0 flex-col">
      <PageHeader />
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-10 overflow-y-auto px-6 py-12">
        <h1 className="text-5xl font-semibold tracking-[0.18em] sm:text-6xl">PUDANS</h1>
        <div className="w-full max-w-3xl">
          <ChatComposer />
        </div>
        <Button variant="outline" size="sm" className="rounded-full text-xs text-muted-foreground">
          <Sparkles />
          探索灵感
        </Button>
      </div>
    </div>
  );
}
