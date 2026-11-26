"use client";

import type { ReactNode } from "react";

import { Switch } from "tdesign-react";

type SwitchFieldProps = {
    description?: ReactNode;
    disabled?: boolean;
    onChange?: (value: boolean) => void;
    value?: boolean;
};

export function SwitchField({ description, disabled, onChange, value }: SwitchFieldProps) {
    return (
        <div className="flex items-center gap-2">
            <Switch disabled={disabled} value={value} onChange={(nextValue) => onChange?.(Boolean(nextValue))} />
            {description ? (
                <span className="text-sm leading-[1.8] text-[var(--td-text-color-secondary)]">{description}</span>
            ) : null}
        </div>
    );
}

SwitchField.displayName = "SwitchField";
