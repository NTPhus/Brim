<script setup lang="ts">
import { useBoardMotion } from '@/composables/useBoardMotion'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRoom } from '@/api/rooms'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { MessageType } from '@/types/game'
import type { GameMessage, GameRoom } from '@/types/game'
import type { ConnectFourState } from '@/types/gameState'

const roomId = String(useRoute().params.roomId)
const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()
const room = ref<GameRoom | null>(null)
const state = ref<ConnectFourState | null>(null)
const motionBoard=ref<HTMLElement|null>(null)
useBoardMotion(motionBoard,()=>state.value?{sequence:state.value.moveCount,tokens:state.value.board.flatMap((row,r)=>row.map((cell,c)=>({key:r+','+c,kind:cell?String(cell):'',effect:'drop' as const})))}:null)
const error = ref('')
const pending = ref(false)
const cleanup: Array<() => void> = []
let disposed = false
let pendingTimer: number | undefined
const myTurn = computed(() => state.value?.status === 'IN_PROGRESS' && state.value.currentPlayer === session.player.id)
const finished = computed(() => state.value ? state.value.status !== 'IN_PROGRESS' : room.value?.status === 'FINISHED')
const name = (id: string | null) => room.value?.players.find(player => player.id === id)?.username ?? 'Đối thủ'
const message = computed(() => {
  if (!stomp.connected.value) return 'Đang kết nối lại…'
  if (!state.value) return finished.value ? 'Ván đã kết thúc. Chủ phòng có thể bắt đầu ván mới.' : 'Đang tải bàn chơi…'
  if (state.value.status === 'DRAW') return 'Hòa! Bàn chơi đã đầy.'
  if (finished.value) return state.value.winner === session.player.id ? 'Bạn đã thắng! 🏆' : `${name(state.value.winner)} đã thắng!`
  return myTurn.value ? 'Đến lượt bạn — chọn một cột để thả quân' : `Đang chờ ${name(state.value.currentPlayer)}…`
})

function receive(next: ConnectFourState) {
  state.value = next
  pending.value = false
  window.clearTimeout(pendingTimer)
  error.value = ''
}
function send(action: string, payload: unknown) {
  stomp.publish(`/app/turn/${roomId}/${action}`, {
    type: action === 'move' ? MessageType.MAKE_MOVE : MessageType.ACTION,
    roomId, playerId: session.player.id, payload, timestamp: Date.now()
  })
}
function drop(column: number) {
  if (!myTurn.value || pending.value || !stomp.connected.value || !state.value?.legalColumns.includes(column)) return
  pending.value = true
  error.value = ''
  send('move', { column })
  pendingTimer = window.setTimeout(() => {
    pending.value = false
    error.value = 'Chưa nhận được phản hồi. Hãy kiểm tra kết nối.'
  }, 5000)
}
function winning(row: number, column: number) {
  return state.value?.winningCells.some(cell => cell.row === row && cell.column === column)
}
onMounted(async () => {
  try {
    room.value = await getRoom(roomId)
    if (disposed) return
    if (room.value.gameType !== 'CONNECT_FOUR' || room.value.status === 'WAITING') {
      await router.replace(`/room/${roomId}`)
      return
    }
    await stomp.connect()
    if (disposed) return
    cleanup.push(
      stomp.subscribe<ConnectFourState>(`/topic/game/${roomId}/state`, receive),
      stomp.subscribe<GameMessage<{ finalState?: ConnectFourState }>>(`/topic/game/${roomId}/over`, msg => {
        if (msg.payload.finalState) receive(msg.payload.finalState)
      }),
      stomp.subscribe<GameRoom>(`/topic/room/${roomId}/state`, next => { room.value = next; session.setCurrentRoom(next) }),
      stomp.subscribe(`/topic/room/${roomId}/deleted`, () => { void router.replace('/') }),
      stomp.subscribe<GameMessage<string>>('/user/queue/errors', msg => {
        error.value = msg.payload
        pending.value = false
        window.clearTimeout(pendingTimer)
      })
    )
    // Subscribe before joining so the initial state cannot arrive before the listener.
    stomp.publish(`/app/turn/${roomId}/join`, {
      type: MessageType.JOIN_ROOM, roomId, playerId: session.player.id,
      payload: { ...session.player, score: 0, ready: true, state: 'PLAYING' }, timestamp: Date.now()
    })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Không thể tải game'
  }
})
onUnmounted(() => {
  disposed = true
  cleanup.forEach(unsubscribe => unsubscribe())
  window.clearTimeout(pendingTimer)
})
</script>

<template>
  <section class="connect-four card" :class="{ 'game-finished':finished }">
    <header>
      <div><h1>🔴 Connect Four</h1><p>{{ room?.name }}</p></div>
      <button class="secondary" @click="router.push('/')">Rời bàn</button>
    </header>
    <p class="status" role="status" aria-live="polite">{{ message }}</p>
    <div v-if="state" class="players">
      <span :class="{ active: state.currentPlayer === state.player1 && !finished }">● Đỏ: {{ name(state.player1) }}{{ state.player1 === session.player.id ? ' (bạn)' : '' }}</span>
      <span :class="{ active: state.currentPlayer === state.player2 && !finished }">○ Vàng: {{ name(state.player2) }}{{ state.player2 === session.player.id ? ' (bạn)' : '' }}</span>
    </div>
    <div v-if="state" ref="motionBoard" class="board" aria-label="Bàn Connect Four, 7 cột, 6 hàng">
      <button v-for="column in 7" :key="column" class="column"
        :aria-label="`Thả quân vào cột ${column}`"
        :disabled="!myTurn || pending || !stomp.connected.value || !state.legalColumns.includes(column - 1)"
        @click="drop(column - 1)">
        <span class="column-number">{{ column }} ↓</span>
        <span v-for="row in 6" :key="row" class="cell" :data-motion-key="`${row-1},${column-1}`"
          :class="{ red: state.board[row - 1][column - 1] === 1, yellow: state.board[row - 1][column - 1] === 2,
            winning: winning(row - 1, column - 1), last: state.lastMove?.row === row - 1 && state.lastMove?.column === column - 1 }"
          :aria-label="`Hàng ${row}: ${['trống', 'đỏ', 'vàng'][state.board[row - 1][column - 1]]}`">
          {{ state.board[row - 1][column - 1] === 1 ? '●' : state.board[row - 1][column - 1] === 2 ? '○' : '' }}
        </span>
      </button>
    </div>
    <p class="rules">Nối ít nhất 4 quân cùng màu theo hàng ngang, dọc hoặc chéo để thắng. Quân rơi xuống ô trống thấp nhất. Đỏ đi trước.</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <button v-if="finished && room?.hostId === session.player.id" :disabled="!stomp.connected.value" @click="send('restart', null)">Chơi ván mới</button>
    <p v-else-if="finished">Đợi chủ phòng bắt đầu ván mới.</p>
  </section>
</template>

<style scoped>
.connect-four { width: 100%; max-width: 660px; padding: clamp(.75rem, 3vw, 2rem); }
header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
h1 { margin: 0; font-size: clamp(1.2rem, 4vw, 1.8rem); }
header p, .rules { color: var(--text-secondary); }
.status { font-weight: 700; min-height: 2.5em; }
.players { display: flex; flex-wrap: wrap; gap: .75rem; margin-bottom: 1rem; }
.players span { padding: .4rem; border: 2px solid transparent; border-radius: 6px; }
.players span:first-child { color: #ff8b99; }
.players span:last-child { color: #ffe17a; }
.players .active { border-color: currentColor; }
.board { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: clamp(3px, 1vw, 8px); padding: clamp(6px, 2vw, 14px); background: #244a9b; border-radius: 16px; }
.column { display: flex; flex-direction: column; gap: clamp(3px, 1vw, 8px); background: transparent; padding: 0; border: 0; border-radius: 5px; min-width: 0; opacity: 1; }
.column:enabled:hover, .column:focus-visible { background: #ffffff25; outline: 2px solid white; }
.column:disabled { cursor: default; }
.column-number { color: white; align-self: center; font-size: .8rem; }
.cell { width: 100%; aspect-ratio: 1; border-radius: 50%; background: #111d38; display: grid; place-items: center; color: #111d38; font-size: clamp(1rem, 5vw, 2rem); box-shadow: inset 0 3px 6px #0005; }
.cell.red { background: #f05a70; }
.cell.yellow { background: #ffd15a; }
.cell.last { outline: 2px dashed white; outline-offset: -5px; }
.cell.winning { outline: 4px solid white; outline-offset: -5px; box-shadow: 0 0 12px #fff8; }
.rules { line-height: 1.6; font-size: .9rem; }
.error { color: #ff8b99; }
</style>
