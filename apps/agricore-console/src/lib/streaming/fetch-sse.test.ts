import { describe, expect, it, vi } from "vitest";

import { readFetchSse, type FetchSseEvent } from "./fetch-sse";

const encoder = new TextEncoder();

function streamFromChunks(chunks: Uint8Array[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(chunk));
      controller.close();
    },
  });
}

function byteChunks(value: string): Uint8Array[] {
  return Array.from(encoder.encode(value), (byte) => Uint8Array.of(byte));
}

describe("readFetchSse", () => {
  it("parses CRLF frames split across UTF-8 byte boundaries", async () => {
    const events: FetchSseEvent[] = [];
    const comments: string[] = [];
    const source = [
      ": heartbeat\r\n",
      "id: 7\r\n",
      "event: delta\r\n",
      "data: Xin\r\n",
      "data: chào 🌱\r\n",
      "\r\n",
    ].join("");

    await readFetchSse(streamFromChunks(byteChunks(source)), {
      onEvent: (event) => {
        events.push(event);
      },
      onComment: (comment) => {
        comments.push(comment);
      },
    });

    expect(comments).toEqual(["heartbeat"]);
    expect(events).toEqual([{ id: "7", event: "delta", data: "Xin\nchào 🌱" }]);
  });

  it("inherits durable IDs and resets event names between LF frames", async () => {
    const events: FetchSseEvent[] = [];
    const source = [
      "id: 3\n",
      "event: status\n",
      "data: first\n\n",
      "retry: 1000\n",
      "unknown: ignored\n",
      "data: second\n\n",
      "id: 4\n\n",
      "data: third\n\n",
    ].join("");

    await readFetchSse(streamFromChunks([encoder.encode(source)]), {
      onEvent: (event) => {
        events.push(event);
      },
    });

    expect(events).toEqual([
      { id: "3", event: "status", data: "first" },
      { id: "3", event: "message", data: "second" },
      { id: "4", event: "message", data: "third" },
    ]);
  });

  it("discards an incomplete event at end of stream", async () => {
    const onEvent = vi.fn();

    await readFetchSse(streamFromChunks([encoder.encode("id: 1\ndata: incomplete")]), {
      onEvent,
    });

    expect(onEvent).not.toHaveBeenCalled();
  });

  it("rejects an oversized unterminated line and cancels the reader", async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode("data: 123456789"));
      },
      cancel,
    });

    await expect(readFetchSse(stream, {
      onEvent: () => undefined,
      maxBufferedCharacters: 8,
    })).rejects.toMatchObject({
      code: "EVENT_STREAM_BUFFER_LIMIT_EXCEEDED",
    });
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("rejects a frame that grows across individually bounded lines", async () => {
    const source = "data: 1234\ndata: 5678\n\n";

    await expect(readFetchSse(streamFromChunks([encoder.encode(source)]), {
      onEvent: () => undefined,
      maxBufferedCharacters: 32,
      maxEventCharacters: 15,
    })).rejects.toMatchObject({
      code: "EVENT_STREAM_FRAME_LIMIT_EXCEEDED",
    });
  });

  it("validates configured limits before acquiring a reader", async () => {
    const stream = streamFromChunks([]);

    await expect(readFetchSse(stream, {
      onEvent: () => undefined,
      maxEventCharacters: 0,
    })).rejects.toThrow(RangeError);
    expect(stream.locked).toBe(false);
  });

  it("fails and cancels a stream that produces no data before the idle deadline", async () => {
    const cancel = vi.fn();
    const reader = {
      read: () => new Promise<ReadableStreamReadResult<Uint8Array>>(() => undefined),
      cancel: () => {
        cancel();
        return Promise.resolve();
      },
      releaseLock: vi.fn(),
    };
    const stream = { getReader: () => reader } as unknown as ReadableStream<Uint8Array>;

    await expect(readFetchSse(stream, {
      onEvent: () => undefined,
      idleTimeoutMs: 10,
    })).rejects.toMatchObject({
      code: "EVENT_STREAM_IDLE_TIMEOUT",
    });
    expect(cancel).toHaveBeenCalledOnce();
  });
});
