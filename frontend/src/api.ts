export async function api<T=any>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch('/api' + path, { credentials: 'same-origin', ...init })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    if (response.status === 401 && !path.startsWith('/auth/') && location.pathname.startsWith('/admin')) location.href = '/admin/login'
    throw new Error(error.message || `请求失败（${response.status}）`)
  }
  return response.json()
}
export function json(method: string, data?: unknown): RequestInit { return { method, headers: { 'Content-Type': 'application/json' }, body: data === undefined ? undefined : JSON.stringify(data) } }
