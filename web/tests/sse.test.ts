import assert from "node:assert/strict";
import test from "node:test";

import { type SseEvent, readSseEvents } from "../src/lib/sse.ts";

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}

async function collect(stream: ReadableStream<Uint8Array>): Promise<SseEvent[]> {
  const events: SseEvent[] = [];
  for await (const event of readSseEvents(stream)) {
    events.push(event);
  }
  return events;
}

test("readSseEvents parses LF and CRLF boundaries", async () => {
  const events = await collect(
    streamOf('event: log\ndata: {"a":1}\n\nevent: log\r\ndata: {"a":2}\r\n\r\n'),
  );
  assert.deepEqual(events, [
    { event: "log", data: '{"a":1}' },
    { event: "log", data: '{"a":2}' },
  ]);
});

test("readSseEvents keeps multi-line data", async () => {
  const events = await collect(streamOf('event: log\ndata: {"a":\ndata: 1}\n\n'));
  assert.deepEqual(events, [{ event: "log", data: '{"a":\n1}' }]);
});

test("readSseEvents skips ping and empty events", async () => {
  const events = await collect(streamOf('event: ping\ndata: {}\n\nevent: log\ndata: {"a":1}\n\n'));
  assert.deepEqual(events, [{ event: "log", data: '{"a":1}' }]);
});

test("readSseEvents handles events split across chunks", async () => {
  const events = await collect(streamOf('event: log\ndata: {"a":', "1}\n\n"));
  assert.deepEqual(events, [{ event: "log", data: '{"a":1}' }]);
});

test("readSseEvents flushes a trailing event", async () => {
  const events = await collect(streamOf('event: log\ndata: {"a":1}'));
  assert.deepEqual(events, [{ event: "log", data: '{"a":1}' }]);
});
