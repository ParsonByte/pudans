import { parseCsv, toCsv } from "@/lib/csv";

import type { CreateLocalUserRequest, UserListItem } from "./api";
import { formatUserDateTime } from "./format";
import { passwordField, usernameField } from "./validation";

const IMPORT_HEADERS = ["用户名", "初始密码", "用户组"] as const;

const MAX_IMPORT_ROWS = 200;

const HEADER_ALIASES = {
  username: ["username", "用户名"],
  password: ["password", "密码", "初始密码"],
  userGroup: ["usergroup", "用户组"],
} as const;

type ImportColumn = keyof typeof HEADER_ALIASES;

const COLUMN_LABELS: Record<ImportColumn, string> = {
  username: "用户名",
  password: "初始密码",
  userGroup: "用户组",
};

export type ParsedUserImport = {
  users: CreateLocalUserRequest[];
  errors: string[];
};

function normalizeHeader(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[\s_-]/g, "");
}

function findColumn(header: string[], column: ImportColumn): number {
  return HEADER_ALIASES[column]
    .map(normalizeHeader)
    .reduce((found, alias) => (found >= 0 ? found : header.indexOf(alias)), -1);
}

export function userImportTemplate(): string {
  return toCsv(IMPORT_HEADERS, []);
}

export function parseUserImport(text: string): ParsedUserImport {
  const rows = parseCsv(text);
  if (rows.length === 0) {
    return { users: [], errors: ["CSV 文件没有内容。"] };
  }

  const header = rows[0].map(normalizeHeader);
  const indexes = {
    username: findColumn(header, "username"),
    password: findColumn(header, "password"),
    userGroup: findColumn(header, "userGroup"),
  };
  const missingColumns = (["username", "password"] as const)
    .filter((column) => indexes[column] < 0)
    .map((column) => COLUMN_LABELS[column]);

  if (missingColumns.length > 0) {
    return { users: [], errors: [`CSV 缺少必填列：${missingColumns.join("、")}。`] };
  }

  const dataRows = rows.slice(1);
  if (dataRows.length === 0) {
    return { users: [], errors: ["CSV 没有数据行。"] };
  }

  const errors: string[] = [];
  if (dataRows.length > MAX_IMPORT_ROWS) {
    errors.push(`单次最多导入 ${MAX_IMPORT_ROWS} 个用户。`);
  }

  const users: CreateLocalUserRequest[] = [];
  const seenUsernames = new Set<string>();

  dataRows.slice(0, MAX_IMPORT_ROWS).forEach((row, offset) => {
    const line = offset + 2;
    const username = (row[indexes.username] ?? "").trim();
    const password = (row[indexes.password] ?? "").trim();
    const userGroup = indexes.userGroup >= 0 ? (row[indexes.userGroup] ?? "").trim() : "";

    if (!username && !password && !userGroup) {
      return;
    }

    const parsedUsername = usernameField.safeParse(username);
    if (!parsedUsername.success) {
      errors.push(`第 ${line} 行：${parsedUsername.error.issues[0]?.message ?? "用户名无效。"}`);
      return;
    }

    const parsedPassword = passwordField.safeParse(password);
    if (!parsedPassword.success) {
      errors.push(`第 ${line} 行：${parsedPassword.error.issues[0]?.message ?? "密码无效。"}`);
      return;
    }

    if (userGroup.length > 63) {
      errors.push(`第 ${line} 行：用户组名称不能超过 63 个字符。`);
      return;
    }

    if (seenUsernames.has(parsedUsername.data)) {
      errors.push(`第 ${line} 行：用户名 ${parsedUsername.data} 在文件中重复。`);
      return;
    }

    seenUsernames.add(parsedUsername.data);
    users.push({
      username: parsedUsername.data,
      password: parsedPassword.data,
      userGroup: userGroup || undefined,
    });
  });

  return { users, errors };
}

const EXPORT_HEADERS = ["用户名", "姓名", "邮箱", "手机号", "状态", "用户组", "注册时间"] as const;

export function usersToCsv(users: UserListItem[]): string {
  return toCsv(
    EXPORT_HEADERS,
    users.map((user) => [
      user.name ?? "",
      user.displayName ?? "",
      user.email ?? "",
      user.phone ?? "",
      user.disabled ? "已禁用" : "正常",
      user.userGroupDisplayName || user.userGroup || "",
      formatUserDateTime(user.registeredAt),
    ]),
  );
}
