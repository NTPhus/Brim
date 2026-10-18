import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameSession } from '@/stores/gameSession'
import { useStompClient } from '@/composables/useStompClient'
import { getRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameMessage, GameRoom, GameTypeCode } from '@/types/game'

export function useBoardSession<T extends { currentPlayer?: string; status?: string; phase?: string; winner?: string | null }>(kind: GameTypeCode) {
  const roomId = String(useRoute().params.roomId)
  const router = useRouter()
  const session = useGameSession()
  const stomp = useStompClient()
  const state = shallowRef<T | null>(null)
  const room = ref<GameRoom | null>(null)
  const error = ref('')
  const pending = ref(false)
  let disposed = false
  let timer: number | undefined
  const cleanup: Array<() => void> = []
  const finished = computed(() => state.value ? ['FINISHED', 'DRAW'].includes(state.value.status ?? state.value.phase ?? '') : room.value?.status === 'FINISHED')
  const myTurn = computed(() => !finished.value && state.value?.currentPlayer === session.player.id)
  const name = (id?: string | null) => room.value?.players.find(p => p.id === id)?.username ?? 'Đối thủ'
  const result = computed(() => !state.value?.winner ? 'Hòa!' : state.value.winner === session.player.id ? 'Bạn đã thắng! 🏆' : `${name(state.value.winner)} đã thắng!`)
  function receive(next: T) {
    if (disposed) return
    state.value = next; pending.value = false; error.value = ''; window.clearTimeout(timer)
  }
  function publish(action: string, payload: unknown) {
    if (!stomp.connected.value) { error.value = 'Đang mất kết nối, hãy chờ kết nối lại.'; return false }
    stomp.publish(`/app/turn/${roomId}/${action}`, {
      type: action === 'move' ? MessageType.MAKE_MOVE : MessageType.ACTION,
      roomId, playerId: session.player.id, payload, timestamp: Date.now()
    })
    return true
  }
  function move(payload: unknown) {
    if (pending.value || finished.value) return
    if (publish('move', payload) && kind !== 'BOMBERMAN') {
      pending.value = true
      timer = window.setTimeout(() => { pending.value = false; error.value = 'Chưa nhận được phản hồi hoặc nước đi không hợp lệ.' }, 4000)
    }
  }
  onMounted(async () => {
    try {
      const nextRoom = await getRoom(roomId)
      if (disposed) return
      room.value = nextRoom
      if (nextRoom.gameType !== kind || nextRoom.status === 'WAITING') { await router.replace(`/room/${roomId}`); return }
      await stomp.connect()
      if (disposed) return
      const destination = `/topic/game/${roomId}/state${kind === 'BATTLESHIP' ? `/${session.player.id}` : ''}`
      cleanup.push(
        stomp.subscribe<T>(destination, receive),
        stomp.subscribe<GameRoom>(`/topic/room/${roomId}/state`, next => { room.value = next; session.setCurrentRoom(next) }),
        stomp.subscribe(`/topic/room/${roomId}/deleted`, () => { void router.replace('/') }),
        stomp.subscribe<{ message: string }>(`/topic/game/${roomId}/error`, next => { error.value = next.message }),
        stomp.subscribe<GameMessage<string>>('/user/queue/errors', next => { error.value = next.payload; pending.value = false; window.clearTimeout(timer) })
      )
      if (kind !== 'BATTLESHIP') cleanup.push(stomp.subscribe<GameMessage<{ finalState: T }>>(`/topic/game/${roomId}/over`, next => receive(next.payload.finalState)))
      stomp.publish(`/app/turn/${roomId}/join`, { type: MessageType.JOIN_ROOM, roomId, playerId: session.player.id,
        payload: { ...session.player, ready: true }, timestamp: Date.now() })
    } catch (cause) { if (!disposed) error.value = cause instanceof Error ? cause.message : 'Không thể tải game' }
  })
  onUnmounted(() => { disposed = true; cleanup.forEach(fn => fn()); window.clearTimeout(timer) })
  return { state, room, error, pending, finished, myTurn, name, result, move, publish, session,
    connected: stomp.connected, restart: () => publish('restart', null), leave: () => router.push('/') }
}
