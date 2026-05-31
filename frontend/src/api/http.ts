export const apiBase = (
  import.meta.env.VITE_API_BASE_URL ||
  `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`
).replace(/\/$/, '');

function normalizeUrl(url: string): string {
  return url.startsWith('/api') ? `${apiBase}${url.slice(4)}` : url;
}

export async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(normalizeUrl(url), {
    headers: { 'Content-Type': 'application/json' },
    ...options,
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
