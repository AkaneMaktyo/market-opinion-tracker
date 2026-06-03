export const apiBase = (
  import.meta.env.VITE_API_BASE_URL ||
  `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`
).replace(/\/$/, '');

function normalizeUrl(url: string): string {
  return url.startsWith('/api') ? `${apiBase}${url.slice(4)}` : url;
}

function requestHeaders(options?: RequestInit): Headers {
  const headers = new Headers(options?.headers);
  if (options?.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  return headers;
}

export async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(normalizeUrl(url), {
    ...options,
    headers: requestHeaders(options),
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text;
    try {
      const payload = JSON.parse(text) as { message?: string };
      message = payload.message || text;
    } catch {
      message = text;
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
