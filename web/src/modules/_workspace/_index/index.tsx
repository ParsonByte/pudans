import { FloatingChatComposer } from "./components/floating-chat-composer";

export function IndexView() {
    return (
        <div className="flex min-h-[calc(100dvh-112px)] min-w-0 flex-col justify-end max-md:min-h-[calc(100dvh-96px)]">
            <FloatingChatComposer />
        </div>
    );
}
