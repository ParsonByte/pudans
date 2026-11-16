import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";

const { listUsersMock } = vi.hoisted(() => ({ listUsersMock: vi.fn() }));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: { num?: number; count?: number }) => {
      if (key === "usersPerPage" && typeof options?.num === "number") {
        return `${options.num} per page`;
      }
      if (key === "usersTotal" && typeof options?.count === "number") {
        return `${options.count} users`;
      }
      return key;
    },
    i18n: { language: "en-US", changeLanguage: vi.fn() },
  }),
}));

vi.mock("@pudans/api-client", () => ({
  coreApiClient: {
    console: {
      user: {
        listUsers: listUsersMock,
      },
    },
  },
}));

import { ConsoleUsersView } from "./users-view";

if (typeof window !== "undefined") {
  if (typeof window.matchMedia !== "function") {
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })) as unknown as typeof window.matchMedia;
  }

  if (typeof window.ResizeObserver !== "function") {
    class ResizeObserverMock {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    }
    window.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;
  }

  if (typeof window.scrollTo !== "function") {
    window.scrollTo = (() => undefined) as typeof window.scrollTo;
  }

  if (typeof Element.prototype.scrollIntoView !== "function") {
    Element.prototype.scrollIntoView = () => undefined;
  }
}

Object.defineProperty(globalThis, "IS_REACT_ACT_ENVIRONMENT", {
  value: true,
  writable: true,
});


const TOTAL_USERS = 30;

const users = Array.from({ length: TOTAL_USERS }, (_, index) => {
  const number = index + 1;
  const padded = String(number).padStart(2, "0");
  return {
    name: `user-${padded}`,
    displayName: `User ${padded}`,
    email: `user${number}@example.com`,
    registeredAt: new Date(Date.UTC(2026, 0, number)).toISOString(),
    disabled: false,
  };
});

interface ListRequest {
  page?: number;
  size?: number;
  keyword?: string;
}

function pageUsers(request: ListRequest) {
  const size = request.size ?? 10;
  const page = Math.max(1, request.page ?? 1);
  const keyword = request.keyword?.toLowerCase();
  const filtered = keyword
    ? users.filter(
        (user) =>
          user.displayName.toLowerCase().includes(keyword) ||
          user.email.toLowerCase().includes(keyword),
      )
    : users;
  const start = (page - 1) * size;

  return Promise.resolve({
    data: {
      items: filtered.slice(start, start + size),
      page,
      size,
      total: filtered.length,
    },
  });
}

function renderUsers() {
  render(<ConsoleUsersView />);
  return screen.findByText("User 01");
}

function pageButton(page: number) {
  return screen.getByRole("button", { name: new RegExp(`page ${page}`, "i") });
}

async function expectLastRequest(expected: ListRequest) {
  await waitFor(() => {
    expect(listUsersMock).toHaveBeenLastCalledWith(expect.objectContaining(expected));
  });
}

describe("ConsoleUsersView pagination", () => {
  beforeEach(() => {
    listUsersMock.mockReset();
    listUsersMock.mockImplementation(pageUsers);
  });

  afterEach(() => {
    cleanup();
    listUsersMock.mockReset();
  });

  it("requests page 1 as page 1 and shows the first page", async () => {
    await renderUsers();

    await expectLastRequest({ page: 1, size: 10 });
    expect(screen.getByText("User 10")).toBeTruthy();
    expect(screen.queryByText("User 11")).toBeNull();
  });

  it("requests page 2 as page 2 and shows the second page", async () => {
    await renderUsers();

    fireEvent.click(pageButton(2));
    await expectLastRequest({ page: 2, size: 10 });
    expect(await screen.findByText("User 11")).toBeTruthy();
    expect(screen.queryByText("User 01")).toBeNull();
  });

  it("requests page 3 as page 3 and shows the third page", async () => {
    await renderUsers();

    fireEvent.click(pageButton(3));
    await expectLastRequest({ page: 3, size: 10 });
    expect(await screen.findByText("User 21")).toBeTruthy();
    expect(screen.queryByText("User 11")).toBeNull();
  });

  it("uses the same 1-based page contract for keyword searches", async () => {
    await renderUsers();

    const search = screen.getByRole("searchbox");
    fireEvent.change(search, { target: { value: "User" } });
    fireEvent.keyDown(search, { key: "Enter" });

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: "User" }),
      );
    });

    fireEvent.click(pageButton(2));
    await expectLastRequest({ page: 2, size: 10, keyword: "User" });
    expect(await screen.findByText("User 11")).toBeTruthy();
  });

  it("keeps the 1-based page contract when the page size changes", async () => {
    await renderUsers();

    fireEvent.mouseDown(screen.getByRole("combobox"));
    fireEvent.click(await screen.findByRole("option", { name: "25 per page" }));
    await waitFor(() => {
      expect(listUsersMock).toHaveBeenCalledWith(expect.objectContaining({ size: 25 }));
    });

    fireEvent.click(pageButton(2));
    await expectLastRequest({ page: 2, size: 25 });
    expect(await screen.findByText("User 26")).toBeTruthy();
    expect(screen.queryByText("User 01")).toBeNull();
  });
});
