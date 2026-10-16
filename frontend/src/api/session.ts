export interface GuestIdentity { id: string; username: string; expiresAt: number }
interface Csrf { headerName: string; token: string }
let csrf: Csrf | null = null
export async function refreshCsrf(): Promise<Csrf> {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error('Không thể tạo phiên bảo mật')
  csrf = await response.json()
  return csrf!
}
export async function csrfHeaders(): Promise<Record<string, string>> {
  const token = csrf ?? await refreshCsrf()
  return { [token.headerName]: token.token }
}
export async function apiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    for (const [key, value] of Object.entries(await csrfHeaders())) headers.set(key, value)
  }
  const response = await fetch(url, { ...options, headers, credentials: 'same-origin', cache: 'no-store' })
  if (response.status === 401 && !url.startsWith('/api/auth/')) window.dispatchEvent(new Event('brim-session-expired'))
  return response
}
export async function resumeOrCreateGuest(username: string): Promise<GuestIdentity> {
  await refreshCsrf()
  const current = await apiFetch('/api/auth/session')
  if (current.ok) return current.json()
  if (current.status !== 401) throw new Error('Không thể khôi phục phiên chơi')
  const created = await apiFetch('/api/auth/guest', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username }) })
  if (!created.ok) throw new Error(created.status === 429 ? 'Tạo phiên quá nhanh, vui lòng thử lại sau' : 'Không thể tạo phiên chơi')
  const identity: GuestIdentity = await created.json()
  await refreshCsrf()
  return identity
}
