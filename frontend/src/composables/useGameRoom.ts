import { ref } from 'vue'
import { useStompClient } from './useStompClient'
import { MessageType } from '@/types/game'
import type { GameMessage, GameRoom, Player } from '@/types/game'

export function useGameRoom() {
  const { connect, subscribe, publish, sessionId } = useStompClient()
  const room = ref<GameRoom | null>(null)
  const error = ref<string | null>(null)
  const cleanupFns = ref<Array<() => void>>([])

  async function join(roomId: string, player: Player, destination: string) {
    try {
      await connect()
      error.value = null

      cleanupFns.value.push(
        subscribe<GameMessage<string>>('/user/queue/errors', (msg) => {
          error.value = msg.payload
        })
      )

      cleanupFns.value.push(
        subscribe<GameRoom>(`/topic/room/${roomId}/state`, (state) => {
          room.value = state
        })
      )

      const message: GameMessage<Player> = {
        type: MessageType.JOIN_ROOM,
        roomId,
        playerId: player.id,
        payload: player,
        timestamp: Date.now()
      }
      publish(`/app/${destination}/${roomId}/join`, message)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      throw e
    }
  }

  function setReady(roomId: string, playerId: string, destination: string) {
    const message: GameMessage<null> = {
      type: MessageType.PLAYER_READY,
      roomId,
      playerId,
      payload: null,
      timestamp: Date.now()
    }
    publish(`/app/${destination}/${roomId}/ready`, message)
  }

  function leave() {
    cleanupFns.value.forEach((fn) => fn())
    cleanupFns.value = []
  }

  return {
    room,
    sessionId,
    error,
    join,
    setReady,
    leave,
    subscribe,
    publish
  }
}
