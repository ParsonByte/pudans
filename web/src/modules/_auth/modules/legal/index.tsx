"use client";

import { Flex, Tabs, Typography } from "antd";

import { LEGAL_DOCUMENTS, LEGAL_KEYS, LEGAL_NOTE, type LegalDocument, type LegalDocumentKey } from "./legal-content";

const { Paragraph, Text, Title } = Typography;

type LegalViewProps = {
    activeTab: LegalDocumentKey;
    onTabChange: (tab: LegalDocumentKey) => void;
};

function LegalArticle({ document }: { document: LegalDocument }) {
    return (
        <Flex className="pb-2" gap={20} vertical>
            <div>
                <Text className="text-[11px] font-semibold tracking-[0.18em]" type="secondary">
                    {document.kicker}
                </Text>
                <Title className="!mt-2 !mb-1" level={4}>
                    {document.title}
                </Title>
                <Text className="!text-xs" type="secondary">
                    更新时间：{document.updated}
                </Text>
                <Paragraph className="!mt-3 !mb-0" type="secondary">
                    {document.intro}
                </Paragraph>
            </div>

            {document.sections.map((section) => (
                <section key={section.heading}>
                    <Title className="!mb-1" level={5}>
                        {section.heading}
                    </Title>
                    {section.paragraphs.map((paragraph) => (
                        <Paragraph className="!mb-2" key={paragraph} type="secondary">
                            {paragraph}
                        </Paragraph>
                    ))}
                </section>
            ))}

            <Paragraph
                className="!mb-0 border-t border-neutral-200 pt-4 !text-xs dark:border-neutral-800"
                type="secondary"
            >
                {LEGAL_NOTE}
            </Paragraph>
        </Flex>
    );
}

export function LegalView({ activeTab, onTabChange }: LegalViewProps) {
    return (
        <Flex gap={20} vertical>
            <div>
                <Text className="text-xs font-semibold tracking-[0.2em]" type="secondary">
                    法律条款 · PUDANS
                </Text>
                <Title className="!mt-1 !mb-0" level={2}>
                    法律协议
                </Title>
            </div>

            <Tabs
                activeKey={activeTab}
                items={LEGAL_KEYS.map((key) => ({
                    children: (
                        <div className="max-h-[min(52svh,520px)] overflow-y-auto pe-3">
                            <LegalArticle document={LEGAL_DOCUMENTS[key]} />
                        </div>
                    ),
                    key,
                    label: LEGAL_DOCUMENTS[key].title,
                }))}
                onChange={(key) => onTabChange(key as LegalDocumentKey)}
            />
        </Flex>
    );
}
