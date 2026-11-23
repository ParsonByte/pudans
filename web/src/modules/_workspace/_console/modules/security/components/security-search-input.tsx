"use client";

import { useRef } from "react";

import { SearchIcon } from "tdesign-icons-react";
import { Input } from "tdesign-react";
import type { InputRef } from "tdesign-react";

type SecuritySearchInputProps = {
    onChange: (value: string) => void;
    onSearch: (value: string) => void;
    placeholder: string;
    value: string;
};

export function SecuritySearchInput({ onChange, onSearch, placeholder, value }: SecuritySearchInputProps) {
    const inputRef = useRef<InputRef>(null);
    const search = (next: string) => onSearch(next.trim());

    return (
        <Input
            clearable
            defaultValue={value}
            key={value}
            onChange={(next) => {
                if (!next) {
                    onChange("");
                    onSearch("");
                }
            }}
            onEnter={(next) => search(String(next ?? ""))}
            placeholder={placeholder}
            ref={inputRef}
            suffixIcon={
                <span className="cursor-pointer" onClick={() => search(inputRef.current?.inputElement.value ?? "")}>
                    <SearchIcon />
                </span>
            }
        />
    );
}
