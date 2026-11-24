"use client";

import { useDreamStore } from "../../stores/use-dream-store";

export function PlayerDiscoveryLog() {
    const entries = useDreamStore((state) => state.discoveryLog);
    const openedPortals = useDreamStore((state) => state.openedPortalIds);

    return (
        <div className="space-y-3">
            <div>
                <p className="text-[10px] tracking-[0.22em] text-white/36 uppercase">最近发现</p>
                {entries.length === 0 ? (
                    <p className="mt-2 text-xs text-white/46">还没有触碰记忆星辰。</p>
                ) : (
                    <ul className="mt-2 space-y-2">
                        {entries.slice(0, 5).map((entry) => (
                            <li className="flex items-center gap-2 text-xs text-white/74" key={entry.id}>
                                <span className="size-1.5 rounded-full bg-cyan-300/70" />
                                {entry.label}
                            </li>
                        ))}
                    </ul>
                )}
            </div>
            <p className="text-[11px] text-white/42">已开启 {openedPortals.length} 道门户</p>
        </div>
    );
}
