import { PageHeader } from "@/components/layout/page-header";

type ConversationHeaderProps = {
  title: string;
};

export function ConversationHeader({ title }: ConversationHeaderProps) {
  return <PageHeader title={title} />;
}
