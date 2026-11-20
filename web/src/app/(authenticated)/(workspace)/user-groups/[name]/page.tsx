"use client";

import { useParams } from "next/navigation";

import { UserGroupDetailView } from "@/modules/workspace/console/user-groups/detail";

export default function UserGroupDetailPage() {
  const params = useParams<{ name: string }>();

  return <UserGroupDetailView name={params.name} />;
}
