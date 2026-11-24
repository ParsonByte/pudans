"use client";

import type { ReactNode } from "react";

export function WorldObjectPool({ children, name }: { children: ReactNode; name: string }) {
    return (
        <group name={name} userData={{ pool: true }}>
            {children}
        </group>
    );
}
