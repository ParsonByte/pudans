"use client";

import { type ReactNode, Suspense } from "react";

export function RegionLoader({ children, label }: { children: ReactNode; label: string }) {
    return (
        <Suspense
            fallback={
                <group>
                    <mesh>
                        <ringGeometry args={[2, 2.04, 64]} />
                        <meshBasicMaterial color="#8c7dff" transparent opacity={0.16} />
                    </mesh>
                </group>
            }
        >
            <group name={label} />
            {children}
        </Suspense>
    );
}
