import { WorkDetailView } from "@/modules/_workspace/_index/modules/create/modules/work-detail";

type WorkDetailPageProps = {
    params: Promise<{ name: string; workspace: string }>;
};

export default async function WorkDetailPage({ params }: WorkDetailPageProps) {
    const { name } = await params;

    return <WorkDetailView name={name} />;
}
