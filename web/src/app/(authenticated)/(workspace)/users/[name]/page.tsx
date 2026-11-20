"use client";

import { useParams } from "next/navigation";

import { UserDetailView } from "@/modules/workspace/console/users/detail";

export default function UserDetailPage() {
  const params = useParams<{ name: string }>();

  return <UserDetailView name={params.name} />;
}
