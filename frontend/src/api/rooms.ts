import { apiFetch } from './session'
import type { CreateRoomRequest, GameRoom, JoinRoomRequest, LeaderboardEntry } from '@/types/game'

const API_BASE = '/api'

export async function createRoom(req: CreateRoomRequest): Promise<GameRoom> {
  const res = await apiFetch(`${API_BASE}/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  })
  if (!res.ok) throw new Error(`Failed to create room: ${res.statusText}`)
  return res.json()
}

export async function getAllRooms(): Promise<GameRoom[]> {
  const res = await apiFetch(`${API_BASE}/rooms`)
  if (!res.ok) throw new Error(`Failed to fetch rooms: ${res.statusText}`)
  return res.json()
}

export async function getRoomsByType(gameType: string): Promise<GameRoom[]> {
  const res = await apiFetch(`${API_BASE}/rooms/type/${gameType}`)
  if (!res.ok) throw new Error(`Failed to fetch rooms: ${res.statusText}`)
  return res.json()
}

export async function getRoom(roomId: string): Promise<GameRoom> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}`)
  if (!res.ok) throw new Error(`Failed to fetch room: ${res.statusText}`)
  return res.json()
}

export async function joinRoom(roomId: string, req: JoinRoomRequest): Promise<GameRoom> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  })
  if (!res.ok) throw new Error(`Failed to join room: ${res.statusText}`)
  return res.json()
}

export async function leaveRoom(roomId: string, playerId: string): Promise<void> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}/leave?playerId=${playerId}`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to leave room: ${res.statusText}`)
}

export async function setPlayerReady(roomId: string, playerId: string, ready: boolean): Promise<void> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}/ready?playerId=${playerId}&ready=${ready}`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to set ready: ${res.statusText}`)
}

export async function addBot(roomId: string): Promise<GameRoom> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}/bot`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to add bot: ${res.statusText}`)
  return res.json()
}

export async function removeBot(roomId: string): Promise<GameRoom> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}/bot`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error(`Failed to remove bot: ${res.statusText}`)
  return res.json()
}

export async function deleteRoom(roomId: string, hostId: string): Promise<void> {
  const res = await apiFetch(`${API_BASE}/rooms/${roomId}?hostId=${encodeURIComponent(hostId)}`, {
    method: 'DELETE'
  })
  // Ignore 400 if room is already gone
  if (!res.ok && res.status !== 404 && res.status !== 400) {
    throw new Error(`Failed to delete room: ${res.statusText}`)
  }
}

export async function getLeaderboard(gameType?: string): Promise<LeaderboardEntry[]> {
  const query = new URLSearchParams({ limit: '12' })
  if (gameType) query.set('gameType', gameType)
  const res = await apiFetch(`${API_BASE}/leaderboard?${query}`)
  if (!res.ok) throw new Error(`Failed to fetch leaderboard: ${res.statusText}`)
  return res.json()
}
