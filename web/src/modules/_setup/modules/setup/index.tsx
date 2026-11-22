"use client";

import { useEffect } from "react";

import { useRouter } from "next/navigation";

import { Typography } from "antd";

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
            <Typography.Title className="!mt-0 !mb-0" level={2}>
                初始化系统
            </Typography.Title>
            {completed ? (
                <Typography.Paragraph className="!mb-0" type="secondary">
                    系统已完成初始化，正在前往登录页。
                </Typography.Paragraph>
            ) : (
                <SetupForm />
            )}
        </div>
    );
}
