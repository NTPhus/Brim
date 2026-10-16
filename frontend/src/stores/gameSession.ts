import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiFetch, resumeOrCreateGuest } from '@/api/session'
import type { GameRoom, Player } from '@/types/game'

export const useGameSession = defineStore('gameSession', () => {
  // IDs in localStorage are deliberately ignored; identity always comes from the server session.
  const player = ref<Player>({ id: '', username: '', sessionId: '', score: 0, ready: false, state: 'WAITING' })
  const currentRoom = ref<GameRoom | null>(null)
  const authenticated = ref(false)
  const profileError = ref('')
  async function initialize() {
    let username = localStorage.getItem('brim_username') || 'Player'
    if (!username.trim() || username.length > 32) username = 'Player'
    const identity = await resumeOrCreateGuest(username)
    player.value = { id: identity.id, username: identity.username, sessionId: identity.id,
      score: 0, ready: false, state: 'WAITING' }
    authenticated.value = true
    localStorage.removeItem('brim_player')
  }
  async function setUsername(username: string) {
    profileError.value = ''
    try {
      const response = await apiFetch('/api/auth/profile', { method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username }) })
      if (!response.ok) throw new Error('Tên phải có 1–32 ký tự và không chứa ký tự điều khiển')
      const identity = await response.json()
      player.value.username = identity.username
      localStorage.setItem('brim_username', identity.username)
    } catch (error) { profileError.value = error instanceof Error ? error.message : 'Không thể đổi tên' }
  }
  async function logout() {
    const response = await apiFetch('/api/auth/logout', { method: 'POST' })
    if (!response.ok && response.status !== 401) throw new Error('Không thể đăng xuất')
    authenticated.value = false
    currentRoom.value = null
    player.value.id = ''
  }
  function setCurrentRoom(room: GameRoom | null) { currentRoom.value = room }
  return { player, currentRoom, authenticated, profileError, initialize, setUsername, setCurrentRoom, logout }
})
