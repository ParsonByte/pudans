import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const repoRoot = new URL("../../", import.meta.url).pathname;
const inventoryPath = join(repoRoot, ".dsh/migration/inventory.json");
const sourceRoot = "/Users/byte/Global/Reference/cloudreve/frontend";
const inventory = JSON.parse(readFileSync(inventoryPath, "utf8"));

test(
  "inventory covers every legacy source file exactly once",
  { skip: !existsSync(sourceRoot) },
  () => {
    const rows = inventory.rows;
    assert.equal(
      new Set(rows.map((row) => row.source)).size,
      rows.length,
      "no duplicate source rows",
    );
    const legacySources = rows
      .filter((row) => row.source.startsWith("src/"))
      .map((row) => row.source);
    assert.ok(legacySources.length >= 800, "src inventory size " + legacySources.length);
    for (const row of rows) {
      assert.ok(row.disposition, "disposition for " + row.source);
      assert.ok(row.status, "status for " + row.source);
      assert.ok(row.reason, "reason for " + row.source);
    }
  },
);

test("completed rows point at real target files", () => {
  const completed = inventory.rows.filter((row) =>
    ["已完成", "已复用", "已保留"].includes(row.status),
  );
  assert.ok(completed.length > 250, "completed rows " + completed.length);
  const broken = completed.filter((row) => {
    if (row.target === "—" || !row.target) return true;
    if (row.source.startsWith("src/component/Icons/")) return false;
    const target = row.target.split(" -> ")[0].replace(/^web\//, "");
    return !existsSync(join(repoRoot, "web", target));
  });
  assert.deepEqual(
    broken.map((row) => row.source + " -> " + row.target),
    [],
  );
});

test("icon coverage is complete", () => {
  const icons = inventory.rows.filter(
    (row) => row.source.startsWith("src/component/Icons/") && row.source.endsWith(".tsx"),
  );
  assert.equal(icons.length, 251);
  assert.ok(
    icons.every((row) => row.status === "已完成"),
    "all icons complete",
  );
  const manifest = JSON.parse(
    readFileSync(join(repoRoot, ".dsh/migration/data/cloudreve-icons.json"), "utf8"),
  );
  assert.equal(manifest.length, 251);
});
