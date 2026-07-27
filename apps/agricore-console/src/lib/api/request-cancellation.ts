export interface RequestCancellation {
  readonly signal: AbortSignal;
  didTimeout(): boolean;
  releaseTimeout(): void;
  dispose(): void;
}

export function createRequestCancellation(
  callerSignal: AbortSignal | undefined,
  timeoutMs: number,
): RequestCancellation {
  const controller = new AbortController();
  let timedOut = false;

  const abortFromCaller = () => controller.abort(callerSignal?.reason);
  if (callerSignal?.aborted) {
    abortFromCaller();
  } else {
    callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
  }

  const timeout = globalThis.setTimeout(() => {
    timedOut = true;
    controller.abort(new DOMException("Request timed out", "TimeoutError"));
  }, timeoutMs);
  const releaseTimeout = () => globalThis.clearTimeout(timeout);

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    releaseTimeout,
    dispose: () => {
      releaseTimeout();
      callerSignal?.removeEventListener("abort", abortFromCaller);
    },
  };
}
