import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { MarkdownContent } from "@/components/content/markdown";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import {
  LEGAL_DOCUMENTS,
  LEGAL_KEYS,
  LEGAL_NOTE,
  type LegalDocument,
  type LegalDocumentKey,
} from "./legal-content";

type LegalViewProps = {
  activeTab: LegalDocumentKey;
  onTabChange: (tab: LegalDocumentKey) => void;
};

function LegalArticle({ document }: { document: LegalDocument }) {
  return (
    <article className="space-y-5 pb-2">
      <div className="space-y-2">
        <p className="text-[11px] font-semibold tracking-[0.18em] text-muted-foreground">
          {document.kicker}
        </p>
        <h2 className="text-xl font-semibold tracking-tight">{document.title}</h2>
        <p className="text-xs text-muted-foreground">更新时间：{document.updated}</p>
        <MarkdownContent
          content={document.intro}
          className="text-sm leading-6 text-muted-foreground"
        />
      </div>

      {document.sections.map((section) => (
        <section key={section.heading} className="space-y-2">
          <h3 className="text-sm font-semibold text-foreground">{section.heading}</h3>
          <MarkdownContent
            content={section.paragraphs.join("\n\n")}
            className="text-sm leading-6 text-muted-foreground"
          />
        </section>
      ))}

      <MarkdownContent
        content={LEGAL_NOTE}
        className="border-t pt-4 text-xs leading-5 text-muted-foreground"
      />
    </article>
  );
}

export function LegalView({ activeTab, onTabChange }: LegalViewProps) {
  return (
    <div className="flex flex-1 flex-col gap-5">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1.5">
          <p className="text-xs font-semibold tracking-[0.2em] text-muted-foreground">
            法律条款 · PUDANS
          </p>
          <h1 className="text-2xl font-semibold tracking-tight">法律协议</h1>
        </div>
        <Link
          href="/sign-in"
          className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          返回登录
        </Link>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(value) => onTabChange(value as LegalDocumentKey)}
        className="gap-4"
      >
        <TabsList className="grid w-full grid-cols-3">
          {LEGAL_KEYS.map((key) => (
            <TabsTrigger key={key} value={key} className="text-xs">
              {LEGAL_DOCUMENTS[key].title}
            </TabsTrigger>
          ))}
        </TabsList>
        {LEGAL_KEYS.map((key) => (
          <TabsContent key={key} value={key}>
            <ScrollArea className="h-[min(52svh,520px)] pe-3">
              <LegalArticle document={LEGAL_DOCUMENTS[key]} />
            </ScrollArea>
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}
