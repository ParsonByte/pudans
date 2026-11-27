import { GenerationDetailView } from "@/modules/_workspace/_index/modules/create/modules/generation-detail";

type GenerationDetailPageProps = {
    params: Promise<{ name: string; workspace: string }>;
};

export default async function GenerationDetailPage({ params }: GenerationDetailPageProps) {
    const { name } = await params;

    return <GenerationDetailView name={name} />;
}
