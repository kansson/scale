/** A failed request, with the HTTP status when the server answered. */
export class HttpError extends Error {
  readonly status: number | null

  constructor(message: string, status: number | null) {
    super(message)
    this.name = 'HttpError'
    this.status = status
  }
}

/** The server took longer than the deadline to answer. */
export class TimeoutError extends HttpError {
  constructor() {
    super('The request timed out', null)
    this.name = 'TimeoutError'
  }
}

/** The connection itself failed, such as with the phone offline. */
export class NetworkError extends HttpError {
  constructor() {
    super('Network error', null)
    this.name = 'NetworkError'
  }
}

const defaultTimeoutMs = 20_000

/**
 * Runs `run` with a signal that aborts at the deadline or when the caller cancels, so a stalled
 * request never leaves the app waiting forever. A missed deadline becomes an `HttpError`;
 * a cancellation passes through untouched.
 */
async function withDeadline<T>(
  signal: AbortSignal | null | undefined,
  run: (signal: AbortSignal) => Promise<T>,
  timeoutMs = defaultTimeoutMs,
) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  const abort = () => controller.abort()

  // A signal aborted before the call never fires its event, so it is passed on at once.
  if (signal?.aborted) {
    abort()
  }

  signal?.addEventListener('abort', abort)

  try {
    return await run(controller.signal)
  } catch (cause) {
    if (signal?.aborted) {
      throw cause
    }

    if (controller.signal.aborted) {
      throw new TimeoutError()
    }

    throw cause
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', abort)
  }
}

/**
 * `fetch` within a deadline that also honours the caller's cancellation. `read` consumes the
 * response inside the deadline, since a body can stall long after the headers arrived.
 */
export async function request<T>(
  url: string,
  init: RequestInit,
  read: (response: Response) => Promise<T>,
  timeoutMs = defaultTimeoutMs,
) {
  return withDeadline(
    init.signal,
    async (signal) => {
      let response: Response

      try {
        response = await fetch(url, { ...init, signal })
      } catch (cause) {
        // `fetch` rejects only when no answer arrived: with a TypeError by the standard, but with
        // an error of its own from expo/fetch, the app's `fetch`, so the kind is not checked.
        if (signal.aborted) {
          throw cause
        }

        throw new NetworkError()
      }

      return read(response)
    },
    timeoutMs,
  )
}
