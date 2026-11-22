import type { ReactNode } from "react";

import { AuthLayout } from "@/modules/_auth";
import "@/modules/_auth/styles/_auth-layout.css";

export default function AuthRouteLayout({ children }: { children: ReactNode }) {
    return <AuthLayout>{children}</AuthLayout>;
}
