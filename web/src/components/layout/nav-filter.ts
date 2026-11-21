import type { NavGroup, NavItem } from "./types";

const CHAT_PATH_PREFIX = "/chat";

export function withoutChatMenus(groups: NavGroup[], keepNewChat = false): NavGroup[] {
  return groups
    .map((group) => ({
      ...group,
      items: group.items
        .map((item) => {
          if (!item.items) {
            return isChatPath(item.url, keepNewChat) ? null : item;
          }
          const children = item.items.filter((subItem) => !isChatPath(subItem.url, keepNewChat));
          return children.length > 0 ? { ...item, items: children } : null;
        })
        .filter((item): item is NavItem => item !== null),
    }))
    .filter((group) => group.items.length > 0);
}

function isChatPath(url: string | undefined, keepNewChat: boolean): boolean {
  return (url === "/" && !keepNewChat) || Boolean(url?.startsWith(CHAT_PATH_PREFIX));
}
