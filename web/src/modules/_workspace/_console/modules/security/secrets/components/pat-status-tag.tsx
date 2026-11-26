"use client";

import type { PersonalAccessToken } from "@pudans-dev/api-client";
import { Tag } from "tdesign-react";

import { PAT_STATUS_META, patStatus } from "../composables/_pat";

type PatStatusTagProps = {
    pat: PersonalAccessToken;
};

export function PatStatusTag({ pat }: PatStatusTagProps) {
    const meta = PAT_STATUS_META[patStatus(pat)];

    return (
        <Tag theme={meta.theme} variant="light">
            {meta.label}
        </Tag>
    );
}
