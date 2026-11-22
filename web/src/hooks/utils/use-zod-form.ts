"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type FieldValues, type UseFormProps, useForm } from "react-hook-form";
import type { z } from "zod";

export function useZodForm<TSchema extends z.ZodType<FieldValues, FieldValues>>(
    schema: TSchema,
    options?: Omit<UseFormProps<z.input<TSchema>, unknown, z.output<TSchema>>, "resolver">,
) {
    return useForm<z.input<TSchema>, unknown, z.output<TSchema>>({
        resolver: zodResolver(schema),
        ...options,
    });
}
