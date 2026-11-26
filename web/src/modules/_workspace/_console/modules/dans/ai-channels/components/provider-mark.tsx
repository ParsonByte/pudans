"use client";

import { useEffect, useState } from "react";

import { type BrandIconFn, Deepseek, Minimax } from "reicon-brands";
import { Sparkle } from "reicon-react";

type ProviderMarkProps = {
    className?: string;
    logoUrl?: string;
    provider: string;
    size?: "large" | "medium" | "small";
};

const SIZE_CLASSES = {
    large: "size-11 text-base",
    medium: "size-9 text-sm",
    small: "size-5 text-[10px]",
} as const;

const LOGO_PADDING_CLASSES = {
    large: "p-1.5",
    medium: "p-1",
    small: "p-0.5",
} as const;

const ICON_SIZES = {
    large: 24,
    medium: 18,
    small: 12,
} as const;

const PROVIDER_COLORS: Record<string, string> = {
    anthropic: "#d97757",
    deepseek: "#3d6dff",
    google: "#4285f4",
    minimax: "#e73562",
    moonshot: "#171717",
    openai: "#10a37f",
    xai: "#111111",
};

const PROVIDER_ICONS: Record<string, BrandIconFn> = {
    deepseek: Deepseek,
    minimax: Minimax,
};

const PROVIDER_BACKGROUNDS: Record<string, string> = {
    deepseek: "#5786fe",
    minimax: "linear-gradient(135deg, #e2167e 0%, #fe603c 100%)",
};

function markColor(provider: string) {
    let hash = 0;
    for (const character of provider) {
        hash = (hash * 31 + character.codePointAt(0)!) % 360;
    }

    return `hsl(${hash} 78% 48%)`;
}

export function ProviderMark({ className = "", logoUrl, provider, size = "medium" }: ProviderMarkProps) {
    const [failedLogo, setFailedLogo] = useState<string>();
    const normalized = provider.trim().toLowerCase();
    const logo = logoUrl?.trim();
    const showLogo = Boolean(logo) && failedLogo !== logo;
    const color = PROVIDER_COLORS[normalized] ?? markColor(normalized);
    const knownProvider = normalized in PROVIDER_COLORS;
    const brandIcon = PROVIDER_ICONS[normalized];

    useEffect(() => {
        if (!logo) {
            return;
        }
        let active = true;
        const image = new window.Image();
        image.onerror = () => {
            if (active) {
                setFailedLogo(logo);
            }
        };
        image.src = logo;
        return () => {
            active = false;
        };
    }, [logo]);

    if (showLogo) {
        return (
            <span
                className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white ring-1 ring-[var(--td-border-level-1-color)] ${SIZE_CLASSES[size]} ${LOGO_PADDING_CLASSES[size]} ${className}`}
            >
                <span
                    aria-label={`${provider} Logo`}
                    className="block size-full bg-contain bg-center bg-no-repeat"
                    role="img"
                    style={{ backgroundImage: `url(${JSON.stringify(logo)})` }}
                />
            </span>
        );
    }

    return (
        <span
            aria-hidden
            className={`inline-flex shrink-0 items-center justify-center rounded-lg font-semibold text-white shadow-sm ${SIZE_CLASSES[size]} ${className}`}
            style={{ background: PROVIDER_BACKGROUNDS[normalized] ?? color }}
        >
            {brandIcon ? (
                <span
                    className="[&>svg]:block"
                    dangerouslySetInnerHTML={{
                        __html: brandIcon.toSvg({ color: "#ffffff", size: ICON_SIZES[size] }),
                    }}
                />
            ) : knownProvider ? (
                normalized.slice(0, 1).toUpperCase()
            ) : (
                <Sparkle size={ICON_SIZES[size]} />
            )}
        </span>
    );
}
