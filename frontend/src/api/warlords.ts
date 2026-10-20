import { apiFetch } from './session'
import type { WarlordsHub, WarlordsProfile } from '@/types/warlords'

async function result<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(typeof body?.message === 'string' ? body.message : response.status === 401
      ? 'Phiên chơi đã hết hạn. Hãy tạo lại phiên guest.' : 'Không thể tải doanh trại. Vui lòng thử lại.')
  }
  return response.json()
}
export async function getWarlordsHub(): Promise<WarlordsHub> { return result(await apiFetch('/api/warlords')) }
export async function selectWarlordsHero(heroId: string): Promise<WarlordsProfile> {
  return result(await apiFetch('/api/warlords/hero', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ heroId }) }))
}
export async function upgradeWarlords(type: string): Promise<WarlordsProfile> {
  return result(await apiFetch('/api/warlords/upgrade', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type }) }))
}
