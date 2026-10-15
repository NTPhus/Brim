import { createId } from '@/utils/id'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GameRoom, Player } from '@/types/game'

const STORAGE_KEY = 'brim_player'

interface StoredPlayer {
  id: string
  username: string
  sessionId: string
}

export const useGameSession = defineStore('gameSession', () => {
  const player = ref<Player>(loadPlayer())
  const currentRoom = ref<GameRoom | null>(null)

  function loadPlayer(): Player {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed: StoredPlayer = JSON.parse(stored)
      return {
        id: parsed.id,
        username: parsed.username,
        sessionId: parsed.sessionId,
        score: 0,
        ready: false,
        state: 'WAITING'
      }
    }
    const newPlayer: Player = {
      id: createId(),
      username: 'Player_' + Math.random().toString(36).slice(2, 7),
      sessionId: createId(),
      score: 0,
      ready: false,
      state: 'WAITING'
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      id: newPlayer.id,
      username: newPlayer.username,
      sessionId: newPlayer.sessionId
    }))
    return newPlayer
  }

  function setUsername(username: string) {
    player.value.username = username
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      id: player.value.id,
      username: player.value.username,
      sessionId: player.value.sessionId
    }))
  }

  function setCurrentRoom(room: GameRoom | null) {
    currentRoom.value = room
  }

  return { player, currentRoom, setUsername, setCurrentRoom }
})
