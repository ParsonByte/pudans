import { UserDetailView } from "@/modules/_workspace/_console/modules/identity/users/detail";

type UserDetailPageProps = {
    params: Promise<{ name: string; workspace: string }>;
};

export default async function UserDetailPage({ params }: UserDetailPageProps) {
    const { name, workspace } = await params;

    return <UserDetailView name={name} workspace={workspace} />;
}
