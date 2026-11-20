import assert from "node:assert/strict";
import test from "node:test";

import {
  beijingDateKey,
  beijingDateTimeInputToIso,
  beijingTimestamp,
  formatBeijingClock,
  formatBeijingDateTime,
  serverToday,
  startOfBeijingDay,
} from "../src/lib/date.ts";

test("beijingDateKey rolls the date over at 16:00 UTC", () => {
  assert.equal(beijingDateKey("2026-11-19T15:59:59Z"), "2026-11-19");
  assert.equal(beijingDateKey("2026-11-19T16:00:00Z"), "2026-11-20");
});

test("Beijing formatters render the Beijing wall clock", () => {
  assert.equal(formatBeijingDateTime("2026-11-19T15:59:00Z"), "2026-11-19 23:59");
  assert.equal(formatBeijingDateTime("2026-11-19T16:00:00Z"), "2026-11-20 00:00");
  assert.equal(formatBeijingClock("2026-11-19T16:00:00Z"), "00:00:00");
});

test("startOfBeijingDay returns the Beijing midnight instant", () => {
  assert.equal(startOfBeijingDay("2026-11-20T03:00:00Z").toISOString(), "2026-11-19T16:00:00.000Z");
});

test("beijingDateTimeInputToIso reads the input as Beijing wall time", () => {
  assert.equal(beijingDateTimeInputToIso("2026-11-20T00:00"), "2026-11-19T16:00:00.000Z");
  assert.equal(beijingDateTimeInputToIso("not-a-date"), undefined);
});

test("beijingTimestamp and serverToday follow the Beijing clock", () => {
  assert.equal(beijingTimestamp(new Date("2026-11-19T16:00:00Z")), "20261120-0000");
  assert.equal(serverToday(), beijingDateKey(new Date()));
});
