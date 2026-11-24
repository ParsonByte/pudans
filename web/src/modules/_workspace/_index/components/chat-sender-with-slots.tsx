"use client";

import { type ComponentProps, type ForwardRefExoticComponent, type ReactNode, type RefAttributes } from "react";

import dynamic from "next/dynamic";

import type { ChatSender as TdChatSender } from "@tdesign-react/chat";

const ChatSender = dynamic(() => import("@tdesign-react/chat").then((module) => module.ChatSender), { ssr: false });

type ChatSenderProps = Omit<ComponentProps<typeof TdChatSender>, "ref">;

type ChatSenderSlotProps = {
    className?: string;
    footerPrefix?: ReactNode;
    innerHeader?: ReactNode;
    inputPrefix?: ReactNode;
};

export const ChatSenderWithSlots = ChatSender as unknown as ForwardRefExoticComponent<
    ChatSenderProps & ChatSenderSlotProps & RefAttributes<HTMLElement>
>;
