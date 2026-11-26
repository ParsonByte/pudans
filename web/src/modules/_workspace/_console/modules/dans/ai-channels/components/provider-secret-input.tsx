"use client";

import { useState } from "react";

import { BrowseIcon, BrowseOffIcon } from "tdesign-icons-react";
import { Button, Input } from "tdesign-react";

type ProviderSecretInputProps = {
    onBlur?: () => void;
    onChange: (value: string) => void;
    placeholder?: string;
    value: string;
};

export function ProviderSecretInput({ onBlur, onChange, placeholder, value }: ProviderSecretInputProps) {
    const [visible, setVisible] = useState(false);

    return (
        <Input
            autocomplete="new-password"
            clearable
            onBlur={onBlur}
            onChange={onChange}
            placeholder={placeholder}
            suffixIcon={
                <Button
                    aria-label={visible ? "隐藏 API Key" : "显示 API Key"}
                    icon={visible ? <BrowseOffIcon /> : <BrowseIcon />}
                    onClick={() => setVisible((current) => !current)}
                    shape="square"
                    type="button"
                    variant="text"
                />
            }
            type={visible ? "text" : "password"}
            value={value}
        />
    );
}
