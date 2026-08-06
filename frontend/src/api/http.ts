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
  const fullUrl = normalizeUrl(url);
  let response: Response;
  try {
    response = await fetch(fullUrl, {
      ...options,
      headers: requestHeaders(options),
    });
  } catch (networkError) {
    throw new Error(`网络请求失败 [${fullUrl.slice(0, 60)}]: ${networkError instanceof Error ? networkError.message : String(networkError)}`);
  }
  const contentType = response.headers.get('content-type') || '';
  if (!response.ok) {
    const text = await response.text();
    let message = text;
    try {
      const payload = JSON.parse(text) as { message?: string };
      message = payload.message || text;
    } catch {
      message = text.slice(0, 200);
    }
    throw new Error(`HTTP ${response.status} [${fullUrl.slice(0, 60)}]: ${message}`);
  }
  // Detect HTML responses (SPA fallback) before JSON parsing
  if (contentType.includes('text/html')) {
    const bodyPreview = await response.text().catch(() => '');
    throw new Error(`API 返回了 HTML 而非 JSON\nURL: ${fullUrl}\nStatus: ${response.status}\nContent-Type: ${contentType}\nBody: ${bodyPreview.slice(0, 100)}`);
  }
  return response.json() as Promise<T>;
}
