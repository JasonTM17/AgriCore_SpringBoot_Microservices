const DEFAULT_MAX_BUFFERED_CHARACTERS = 256 * 1024;
const DEFAULT_MAX_EVENT_CHARACTERS = 128 * 1024;
const DECODE_SLICE_BYTES = 16 * 1024;

export interface FetchSseEvent {
  id: string;
  event: string;
  data: string;
}

export interface FetchSseOptions {
  onEvent: (event: FetchSseEvent) => void | Promise<void>;
  onComment?: (comment: string) => void | Promise<void>;
  maxBufferedCharacters?: number;
  maxEventCharacters?: number;
}

export class FetchSseError extends Error {
  readonly code: "EVENT_STREAM_BUFFER_LIMIT_EXCEEDED" | "EVENT_STREAM_FRAME_LIMIT_EXCEEDED";

  constructor(code: FetchSseError["code"], message: string) {
    super(message);
    this.name = "FetchSseError";
    this.code = code;
  }
}

function positiveLimit(value: number | undefined, fallback: number, name: string): number {
  const limit = value ?? fallback;
  if (!Number.isSafeInteger(limit) || limit < 1) {
    throw new RangeError(`${name} must be a positive safe integer`);
  }
  return limit;
}

export async function readFetchSse(
  stream: ReadableStream<Uint8Array>,
  options: FetchSseOptions,
): Promise<void> {
  const maxBuffered = positiveLimit(
    options.maxBufferedCharacters,
    DEFAULT_MAX_BUFFERED_CHARACTERS,
    "maxBufferedCharacters",
  );
  const maxEvent = positiveLimit(
    options.maxEventCharacters,
    DEFAULT_MAX_EVENT_CHARACTERS,
    "maxEventCharacters",
  );
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let dataLines: string[] = [];
  let eventName = "";
  let lastEventId = "";
  let eventCharacters = 0;
  let completed = false;

  const resetEvent = () => {
    dataLines = [];
    eventName = "";
    eventCharacters = 0;
  };

  const dispatchEvent = async () => {
    if (dataLines.length > 0) {
      await options.onEvent({
        id: lastEventId,
        event: eventName || "message",
        data: dataLines.join("\n"),
      });
    }
    resetEvent();
  };

  const processLine = async (line: string) => {
    if (line === "") {
      await dispatchEvent();
      return;
    }
    if (line.startsWith(":")) {
      const comment = line.slice(1).replace(/^ /, "");
      await options.onComment?.(comment);
      return;
    }

    eventCharacters += line.length;
    if (eventCharacters > maxEvent) {
      throw new FetchSseError(
        "EVENT_STREAM_FRAME_LIMIT_EXCEEDED",
        "Event stream frame exceeded the configured limit",
      );
    }

    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) value = value.slice(1);

    if (field === "data") {
      dataLines.push(value);
    } else if (field === "event") {
      eventName = value;
    } else if (field === "id" && !value.includes("\0")) {
      lastEventId = value;
    }
  };

  const processLines = async (final: boolean) => {
    while (true) {
      const newline = buffer.search(/[\r\n]/);
      if (newline < 0) break;
      const marker = buffer[newline];
      if (!final && marker === "\r" && newline === buffer.length - 1) break;

      const width = marker === "\r" && buffer[newline + 1] === "\n" ? 2 : 1;
      const line = buffer.slice(0, newline);
      buffer = buffer.slice(newline + width);
      await processLine(line);
    }
    if (buffer.length > maxBuffered) {
      throw new FetchSseError(
        "EVENT_STREAM_BUFFER_LIMIT_EXCEEDED",
        "Event stream line exceeded the configured buffer limit",
      );
    }
  };

  const appendDecoded = async (value: string, final = false) => {
    buffer += value;
    await processLines(final);
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      for (let offset = 0; offset < value.length; offset += DECODE_SLICE_BYTES) {
        const slice = value.subarray(offset, offset + DECODE_SLICE_BYTES);
        await appendDecoded(decoder.decode(slice, { stream: true }));
      }
    }
    await appendDecoded(decoder.decode(), true);
    completed = true;
  } finally {
    if (!completed) {
      await reader.cancel().catch(() => undefined);
    }
    reader.releaseLock();
  }
}
