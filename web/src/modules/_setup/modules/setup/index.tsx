"use client";

import { Typography } from "antd";

import { SetupForm } from "./components/setup-form";

export function SetupView() {
    return (
        <div className="flex flex-col gap-5">
            <Typography.Title className="!mt-0 !mb-0" level={2}>
                初始化系统
            </Typography.Title>
            <SetupForm />
        </div>
    );
}
