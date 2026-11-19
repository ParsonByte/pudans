export type ChatPreferences = {
  pinned: string[];
  aliases: Record<string, string>;
  seen: Record<string, string>;
};

const STORAGE_KEY = "pudans-chat-preferences";

const EMPTY: ChatPreferences = { pinned: [], aliases: {}, seen: {} };

export function loadChatPreferences(): ChatPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { ...EMPTY };
    }
    const parsed = JSON.parse(raw) as Partial<ChatPreferences>;
    return {
      pinned: Array.isArray(parsed.pinned) ? parsed.pinned.filter(Boolean) : [],
      aliases: parsed.aliases && typeof parsed.aliases === "object" ? parsed.aliases : {},
      seen: parsed.seen && typeof parsed.seen === "object" ? parsed.seen : {},
    };
  } catch {
    return { ...EMPTY };
  }
}

export function saveChatPreferences(preferences: ChatPreferences): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  } catch {
    // 本地偏好写入失败不影响聊天。
  }
}
