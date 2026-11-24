import { ActivityDetailView } from "@/modules/_workspace/_index/modules/create/modules/activity-detail";

type ActivityDetailPageProps = {
    params: Promise<{ name: string; workspace: string }>;
};

export default async function ActivityDetailPage({ params }: ActivityDetailPageProps) {
    const { name } = await params;

    return <ActivityDetailView name={name} />;
}
