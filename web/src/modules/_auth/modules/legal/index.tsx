"use client";

import { Tabs, Typography } from "tdesign-react";

import { LEGAL_DOCUMENTS, LEGAL_KEYS, LEGAL_NOTE, type LegalDocument, type LegalDocumentKey } from "./legal-content";

type LegalViewProps = {
    activeTab: LegalDocumentKey;
    onTabChange: (tab: LegalDocumentKey) => void;
};

function LegalArticle({ document }: { document: LegalDocument }) {
    return (
        <div className="flex flex-col gap-5 pb-2">
            <div>
                <Typography.Text className="text-[11px] font-semibold tracking-[0.18em]" theme="secondary">
                    {document.kicker}
                </Typography.Text>
                <Typography.Title className="!mt-2 !mb-1" level="h4">
                    {document.title}
                </Typography.Title>
                <Typography.Text className="!text-xs" theme="secondary">
                    更新时间：{document.updated}
                </Typography.Text>
                <Typography.Paragraph className="!mt-3 !mb-0 !text-sm !text-[var(--td-text-color-secondary)]">
                    {document.intro}
                </Typography.Paragraph>
            </div>

            {document.sections.map((section) => (
                <section key={section.heading}>
                    <Typography.Title className="!mb-1" level="h5">
                        {section.heading}
                    </Typography.Title>
                    {section.paragraphs.map((paragraph) => (
                        <p className="mb-2 text-sm text-[var(--td-text-color-secondary)]" key={paragraph}>
                            {paragraph}
                        </p>
                    ))}
                </section>
            ))}

            <Typography.Paragraph className="!mb-0 border-t border-[var(--td-border-level-1-color)] pt-4 !text-xs !text-[var(--td-text-color-secondary)]">
                {LEGAL_NOTE}
            </Typography.Paragraph>
        </div>
    );
}

export function LegalView({ activeTab, onTabChange }: LegalViewProps) {
    return (
        <div className="flex min-h-0 flex-1 flex-col gap-5">
            <div>
                <Typography.Text className="text-xs font-semibold tracking-[0.2em]" theme="secondary">
                    法律条款 · PUDANS
                </Typography.Text>
                <Typography.Title className="!mt-1 !mb-0 !text-[30px] !leading-[1.35]" level="h2">
                    法律协议
                </Typography.Title>
            </div>

            <Tabs
                className="legal-tabs"
                list={LEGAL_KEYS.map((key) => ({
                    label: LEGAL_DOCUMENTS[key].title,
                    panel: <LegalArticle document={LEGAL_DOCUMENTS[key]} />,
                    value: key,
                }))}
                onChange={(key) => onTabChange(key as LegalDocumentKey)}
                value={activeTab}
            />
        </div>
    );
}
