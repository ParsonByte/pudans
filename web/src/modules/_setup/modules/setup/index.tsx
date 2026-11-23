"use client";

import { useEffect } from "react";

import { useRouter } from "next/navigation";

import { Loading, Typography } from "tdesign-react";

import { useSetupStore } from "@/stores/_setup";

import { SetupForm } from "./components/setup-form";

export function SetupView() {
    const router = useRouter();
    const { completed } = useSetupStore();

    useEffect(() => {
        if (completed) {
            router.replace("/sign-in");
        }
    }, [completed, router]);

    return (
        <div className="flex flex-col gap-5">
            <Typography.Title level="h2">初始化系统</Typography.Title>
            {completed ? <Loading loading /> : <SetupForm />}
        </div>
    );
}
