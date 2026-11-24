import { ExplorePlayerView } from "@/modules/_workspace/_index/modules/explore/modules/explore-player";

type ExplorePlayerPageProps = {
    params: Promise<{ name: string; workspace: string }>;
};

export default async function ExplorePlayerPage({ params }: ExplorePlayerPageProps) {
    const { name } = await params;

    return <ExplorePlayerView name={name} />;
}
