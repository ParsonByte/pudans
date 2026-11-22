import type { ReactNode } from "react";

import type { Metadata } from "next";

import { AuthLayout } from "@/modules/_auth";
import "@/modules/_auth/styles/_auth-layout.css";

export const metadata: Metadata = {
    title: "Pudans 初始化",
};

export default function SetupRouteLayout({ children }: { children: ReactNode }) {
    return <AuthLayout variant="setup">{children}</AuthLayout>;
}
