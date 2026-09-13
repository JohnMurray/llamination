export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
  }
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const response = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers,
  });
  if (!response.ok) {
    let message = response.status === 401 ? 'Please sign in to continue.' : 'Something went wrong.';
    let code: string | undefined;
    try {
      const error = (await response.json()) as { message?: string; code?: string };
      message = error.message ?? message;
      code = error.code;
    } catch {
      // The default message is enough for non-JSON failures.
    }
    throw new ApiError(message, response.status, code);
  }
  if (response.status === 204) {
    return null as T;
  }
  return response.json() as Promise<T>;
}
