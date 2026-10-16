<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, leaveRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameRoom } from '@/types/game'
import type { XiangqiGameState, XiangqiPiece } from '@/types/gameState'

const route = useRoute()
const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()

const roomId = route.params.roomId as string
const myPlayerId = session.player.id
const gameState = ref<XiangqiGameState | null>(null)
const roomInfo = ref<GameRoom | null>(null)
const selectedCell = ref<{ x: number; y: number } | null>(null)
const error = ref<string | null>(null)
const isGameOver = ref(false)
const showResult = ref(true)
const cleanupFns = ref<Array<() => void>>([])

// Board: 9 columns (x 0-8), 10 rows (y 0-9)
const COLS = 9
const ROWS = 10

const PIECE_LABELS: Record<string, string> = {
  GENERAL: 'Tướng',
  ADVISOR: 'Sĩ',
  ELEPHANT: 'Tượng',
  HORSE: 'Mã',
  CHARIOT: 'Xe',
  CANNON: 'Pháo',
  SOLDIER: 'Tốt'
}

const pieceAt = computed(() => {
  const map: Record<string, XiangqiPiece> = {}
  if (gameState.value?.pieces) {
    for (const piece of Object.values(gameState.value.pieces)) {
      map[`${piece.x},${piece.y}`] = piece
    }
  }
  return map
})

const myColor = computed(() => {
  if (!gameState.value) return null
  if (gameState.value.redPlayer === myPlayerId) return 'RED'
  if (gameState.value.blackPlayer === myPlayerId) return 'BLACK'
  return null
})

const isMyTurn = computed(
  () => gameState.value?.currentPlayer === myPlayerId
)

const isCheck = computed(() => gameState.value?.check ?? false)
const isCheckmate = computed(() => gameState.value?.checkmate
  ?? (gameState.value?.status === 'FINISHED' && isCheck.value))
const checkedColor = computed(() => {
  const state = gameState.value
  if (!state || !isCheck.value) return null
  return state.currentPlayer === state.redPlayer ? 'RED' : 'BLACK'
})
const checkedGeneralSquare = computed(() => {
  const general = Object.values(pieceAt.value).find(piece => piece.type === 'GENERAL' && piece.color === checkedColor.value)
  return general ? `${general.x},${general.y}` : null
})
const checkMessage = computed(() => {
  const side = checkedColor.value === 'RED' ? 'Đỏ' : 'Đen'
  if (isCheckmate.value) return `Chiếu hết! Tướng ${side} không còn nước thoát.`
  return isMyTurn.value
    ? 'Bạn đang bị chiếu! Hãy di chuyển tướng, che chắn hoặc bắt quân đang chiếu.'
    : `Bạn đang chiếu tướng ${side}. Đối thủ phải hóa giải nước chiếu.`
})
const resultTitle = computed(() => {
  if (isCheckmate.value) return 'Chiếu hết!'
  if (gameState.value?.status === 'DRAW') return 'Hòa cờ'
  if (gameState.value?.status === 'FINISHED' && gameState.value.winner) return 'Hết nước đi — xử thua'
  return 'Ván cờ kết thúc'
})
const gameOverMessage = computed(() => {
  const state = gameState.value
  if (state?.status === 'DRAW') return 'Ván cờ kết thúc hòa.'
  if (!state?.winner) return 'Ván cờ kết thúc!'
  const outcome = state.winner === myPlayerId ? 'Bạn đã thắng! 🏆'
    : `${roomInfo.value?.players.find(p => p.id === state.winner)?.username ?? 'Đối thủ'} đã thắng. Bạn đã thua ván này.`
  return isCheckmate.value ? outcome : `${outcome} Trong cờ tướng, bên hết nước đi hợp lệ bị xử thua dù không bị chiếu.`
})
const statusText = computed(() => {
  if (!gameState.value) return 'Đang kết nối...'
  if (isGameOver.value) return resultTitle.value
  if (isCheck.value) return isMyTurn.value ? '⚠️ Chiếu tướng! Đến lượt bạn' : 'Bạn đang chiếu tướng!'
  return isMyTurn.value ? '🎯 Đến lượt bạn' : '⏳ Đợi đối thủ...'
})
const lastMove = computed(() => gameState.value?.lastMove ?? null)
const historyRows = computed(() => {
  const moves = gameState.value?.moveHistory ?? []
  return Array.from({ length: Math.ceil(moves.length / 2) }, (_, index) => ({
    number: index + 1,
    red: formatMove(moves[index * 2]),
    black: formatMove(moves[index * 2 + 1])
  }))
})

const legalTargets = computed(() => {
  if (!selectedCell.value) return new Set<string>()
  return new Set((gameState.value?.legalMoves ?? [])
    .filter(move => move.fromX === selectedCell.value!.x && move.fromY === selectedCell.value!.y)
    .map(move => `${move.toX},${move.toY}`))
})

function pieceLabel(piece: XiangqiPiece): string {
  const labels: Record<string, [string, string]> = {
    GENERAL: ['帥', '將'], ADVISOR: ['仕', '士'], ELEPHANT: ['相', '象'],
    HORSE: ['傌', '馬'], CHARIOT: ['俥', '車'], CANNON: ['炮', '砲'], SOLDIER: ['兵', '卒']
  }
  return labels[piece.type]?.[piece.color === 'RED' ? 0 : 1] ?? PIECE_LABELS[piece.type]
}

function formatMove(raw?: string): string {
  if (!raw) return '—'
  const match = raw.match(/fromX=(\d+), fromY=(\d+), toX=(\d+), toY=(\d+)/)
  if (!match) return raw.replace(/^ChineseChessMove\[|\]$/g, '')
  const [, fx, fy, tx, ty] = match.map(Number)
  return `(${fx},${fy}) → (${tx},${ty})`
}

watch(() => gameState.value?.currentPlayer, () => {
  selectedCell.value = null
})

watch(() => gameState.value?.status, () => {
  showResult.value = true
  selectedCell.value = null
})

onMounted(async () => {
  try {
    const room = await getRoom(roomId)
    roomInfo.value = room
    if (room.status === 'FINISHED') {
      isGameOver.value = true
    }
  } catch (e) {
    console.warn('[ChineseChessView] Fetch room error:', e)
  }

  try {
    await stomp.connect()

    cleanupFns.value.push(
      stomp.subscribe(`/topic/game/${roomId}/state`, (state: XiangqiGameState) => {
        gameState.value = state
        isGameOver.value = state.status !== 'IN_PROGRESS'
        error.value = null
      })
    )

    cleanupFns.value.push(
      stomp.subscribe(`/topic/room/${roomId}/state`, (room: GameRoom) => {
        roomInfo.value = room
        if (room.status === 'FINISHED') {
          isGameOver.value = true
        }
      })
    )

    cleanupFns.value.push(
      stomp.subscribe(`/topic/game/${roomId}/over`, (msg: { payload?: { finalState?: XiangqiGameState } }) => {
        if (msg.payload?.finalState) gameState.value = msg.payload.finalState
        isGameOver.value = true
      })
    )

    cleanupFns.value.push(
      stomp.subscribe(`/user/queue/errors`, (msg: { payload: string }) => {
        error.value = msg?.payload || 'Nước đi không hợp lệ'
      })
    )

    stomp.publish(`/app/turn/${roomId}/join`, {
      type: MessageType.JOIN_ROOM,
      roomId,
      playerId: myPlayerId,
      payload: {
        id: myPlayerId,
        username: session.player.username,
        sessionId: session.player.sessionId,
        score: 0,
        ready: true,
        state: 'PLAYING'
      }
    })

    setTimeout(() => {
      stomp.publish(`/app/turn/${roomId}/ready`, {
        type: MessageType.JOIN_ROOM,
        roomId,
        playerId: myPlayerId,
        payload: null
      })
    }, 400)

  } catch (err: any) {
    error.value = err.message || 'Không thể kết nối máy chủ'
  }
})

onUnmounted(() => {
  cleanupFns.value.forEach((fn) => fn())
  // Don't disconnect the shared STOMP client — other views may still use it
})

async function handleGoToLobby() {
  try {
    await leaveRoom(roomId, myPlayerId)
  } catch (e) {
    console.warn('Leave room error (non-fatal):', e)
  } finally {
    router.push('/')
  }
}

function onCellClick(x: number, y: number) {
  if (!isMyTurn.value || isGameOver.value || gameState.value?.status !== 'IN_PROGRESS') return
  error.value = null
  const piece = pieceAt.value[`${x},${y}`]

  if (selectedCell.value) {
    if (selectedCell.value.x === x && selectedCell.value.y === y) {
      selectedCell.value = null
      return
    }

    if (piece?.color === myColor.value) {
      selectedCell.value = { x, y }
      return
    }

    if (!legalTargets.value.has(`${x},${y}`)) {
      error.value = 'Ô này không phải là nước đi hợp lệ'
      return
    }

    stomp.publish(`/app/turn/${roomId}/move`, {
      type: MessageType.MAKE_MOVE,
      roomId,
      playerId: myPlayerId,
      payload: { fromX: selectedCell.value.x, fromY: selectedCell.value.y, toX: x, toY: y },
      timestamp: Date.now()
    })
    selectedCell.value = null
    return
  }

  if (piece && isMyTurn.value) {
    if (!myColor.value || piece.color === myColor.value) {
      selectedCell.value = { x, y }
    }
  }
}

function handleRestart() {
  selectedCell.value = null
  error.value = null
  stomp.publish(`/app/turn/${roomId}/restart`, {
    type: MessageType.ACTION,
    roomId,
    playerId: myPlayerId,
    payload: null
  })
}
</script>

<template>
  <div class="xiangqi-game">
    <div class="game-header">
      <button class="secondary" @click="handleGoToLobby">← Về sảnh</button>
      <div class="status" :class="{ mine: isMyTurn && !isGameOver, check: isCheck }" role="status" aria-live="polite">
        {{ statusText }}
      </div>
      <div class="color-badge" v-if="myColor">
        Quân của bạn: <strong :class="myColor.toLowerCase()">{{ myColor === 'RED' ? 'Đỏ 🔴' : 'Đen ⚫' }}</strong>
      </div>
    </div>

    <div v-if="error" class="error-msg">⚠️ {{ error }}</div>

    <div v-if="isCheck" class="check-banner" :class="{ checkmate: isCheckmate }" role="status" aria-live="polite">
      <span class="check-icon" aria-hidden="true">{{ isCheckmate ? '將' : '⚠' }}</span>
      <div>
        <strong>{{ isCheckmate ? 'CHIẾU HẾT' : 'CHIẾU TƯỚNG' }}</strong>
        <p>{{ checkMessage }}</p>
      </div>
    </div>
    <button v-if="isGameOver && !showResult" class="btn-secondary" @click="showResult = true">Xem kết quả</button>

    <div class="board-wrapper">
      <div class="xiangqi-board">
        <!-- 10 rows (y: 0 to 9) -->
        <div
          v-for="r in ROWS"
          :key="`row-${r}`"
          class="board-row"
          :class="{ river: (r - 1) === 4 }"
        >
          <!-- 9 cols (x: 0 to 8) -->
          <div
            v-for="c in COLS"
            :key="`col-${c}`"
            class="cell"
            :class="{
              selected: selectedCell?.x === (c - 1) && selectedCell?.y === (r - 1),
              'last-from': lastMove?.fromX === c - 1 && lastMove?.fromY === r - 1,
              'last-to': lastMove?.toX === c - 1 && lastMove?.toY === r - 1,
              'legal-target': legalTargets.has(`${c - 1},${r - 1}`),
              'general-in-check': checkedGeneralSquare === `${c - 1},${r - 1}`,
              'general-checkmated': isCheckmate && checkedGeneralSquare === `${c - 1},${r - 1}`,
              capture: legalTargets.has(`${c - 1},${r - 1}`) && !!pieceAt[`${c - 1},${r - 1}`]
            }"
            :title="checkedGeneralSquare === `${c - 1},${r - 1}` ? (isCheckmate ? 'Tướng bị chiếu hết' : 'Tướng đang bị chiếu') : undefined"
            @click="onCellClick(c - 1, r - 1)"
          >
            <span
              v-if="pieceAt[`${c - 1},${r - 1}`]"
              class="piece"
              :class="pieceAt[`${c - 1},${r - 1}`]?.color === 'RED' ? 'red' : 'black'"
            >
              <span class="piece-label">
                {{ pieceLabel(pieceAt[`${c - 1},${r - 1}`]!) }}
              </span>
            </span>
            <span v-if="checkedGeneralSquare === `${c - 1},${r - 1}`" class="general-danger" aria-hidden="true">!</span>
            <span v-if="selectedCell?.x === (c - 1) && selectedCell?.y === (r - 1)" class="selected-ring" />
            <span v-else-if="legalTargets.has(`${c - 1},${r - 1}`)" class="move-preview" />
          </div>
        </div>
      </div>

      <aside class="history" v-if="gameState?.moveHistory?.length">
        <h4>Lịch sử nước đi</h4>
        <div class="history-columns"><span>#</span><span>Đỏ</span><span>Đen</span></div>
        <div class="move-list">
          <div v-for="row in historyRows" :key="row.number" class="move-row">
            <b>{{ row.number }}</b><span>{{ row.red }}</span><span>{{ row.black }}</span>
          </div>
        </div>
      </aside>

      <!-- Game Over Modal Overlay -->
      <div v-if="isGameOver && showResult" class="modal-overlay">
        <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="xiangqi-result-title" aria-describedby="xiangqi-result-detail">
          <h3 id="xiangqi-result-title">{{ resultTitle }}</h3>
          <p id="xiangqi-result-detail">{{ gameOverMessage }}</p>
          <div class="modal-actions">
            <button class="btn-secondary" autofocus @click="showResult = false">Xem bàn cờ</button>
            <button class="btn-primary" @click="handleRestart">🎮 Chơi lại</button>
            <button class="btn-secondary" @click="router.push(`/room/${roomId}`)">🏠 Phòng chờ</button>
            <button class="btn-secondary" @click="handleGoToLobby">📋 Về sảnh</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isMyTurn && !isGameOver && !isCheck" class="hint">
      💡 Nhấp vào quân của bạn để chọn, sau đó nhấp vào ô đích để đi
    </div>
  </div>
</template>

<style scoped>
.xiangqi-game {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  width: 100%;
  max-width: 900px;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.game-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.status {
  font-weight: 600;
  padding: 0.4rem 1rem;
  border-radius: 999px;
  background: var(--bg-card);
  font-size: 0.9rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.status.mine {
  color: var(--success);
  background: rgba(46, 204, 113, 0.15);
  border-color: rgba(46, 204, 113, 0.4);
}

.status.check {
  color: var(--danger);
  background: rgba(231, 76, 60, 0.15);
}

.color-badge {
  font-size: 0.85rem;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 0.3rem 0.7rem;
  border-radius: 8px;
}

.color-badge strong.red {
  color: #e74c3c;
}

.color-badge strong.black {
  color: #fff;
}

.error-msg {
  color: var(--danger);
  background: rgba(231, 76, 60, 0.1);
  padding: 0.6rem 1rem;
  border-radius: 8px;
  border: 1px solid rgba(231, 76, 60, 0.3);
  font-size: 0.9rem;
}

.board-wrapper {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: 1rem;
}

.xiangqi-board {
  background: #deb887;
  border: 4px solid #8b4513;
  border-radius: 8px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
  padding: 8px;
}

.board-row {
  display: flex;
}

.board-row.river {
  border-bottom: 2px dashed rgba(139, 69, 19, 0.4);
}

.cell {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  border: 1px solid rgba(139, 69, 19, 0.25);
  user-select: none;
}

.cell.selected {
  background: rgba(46, 204, 113, 0.3);
}

.cell.last-from { box-shadow: inset 0 0 0 3px rgba(251, 191, 36, .6); }
.cell.last-to { background: rgba(250, 204, 21, .62); }

.cell.legal-target { background: rgba(46, 204, 113, 0.28); }

.move-preview {
  position: absolute;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(20, 100, 45, 0.65);
  pointer-events: none;
}

.capture .move-preview {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(20, 100, 45, 0.7);
  background: transparent;
}

.piece {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 1rem;
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.4);
  transition: transform 0.15s;
}

.piece:hover {
  transform: scale(1.08);
}

.piece.red {
  background: #fdf2e9;
  border: 2px solid #c0392b;
  color: #c0392b;
}

.piece.black {
  background: #f5f5f5;
  border: 2px solid #2c3e50;
  color: #2c3e50;
}

.piece-label {
  font-weight: 700;
  font-size: 0.95rem;
}

.selected-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 3px solid #2ecc71;
  pointer-events: none;
}

.history { width: 210px; background: var(--bg-card); padding: 1rem; border-radius: 12px; border: 1px solid var(--border); }
.history h4 { margin: 0 0 .5rem; font-size: .85rem; color: var(--text-secondary); }
.history-columns { display: grid; grid-template-columns: 22px 1fr 1fr; gap: .3rem; padding: 0 .35rem .25rem; color: var(--text-muted); font-size: .62rem; text-transform: uppercase; }
.move-list { display: flex; flex-direction: column; gap: .35rem; max-height: 360px; overflow-y: auto; }
.move-row { display: grid; grid-template-columns: 22px 1fr 1fr; gap: .3rem; align-items: center; padding: .32rem .35rem; border-radius: 6px; background: rgba(255,255,255,.05); font-size: .7rem; }
.move-row b { color: #fbbf24; }
.move-row span { color: var(--text-secondary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; white-space: nowrap; }

@media (max-width: 760px) {
  .board-wrapper { flex-direction: column; align-items: center; }
  .history { width: 100%; }
  .cell { width: min(10vw, 52px); height: min(10vw, 52px); }
}

.check-banner {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  width: 100%;
  box-sizing: border-box;
  padding: 0.85rem 1rem;
  border: 1px solid #f87171;
  border-left: 5px solid #ef4444;
  border-radius: 12px;
  background: #3b1922;
  color: #fecaca;
}

.check-banner strong { letter-spacing: 0.06em; }
.check-banner p { margin: 0.3rem 0 0; font-size: 0.9rem; }
.check-icon { font-size: 2rem; }
.check-banner.checkmate { background: #4c1525; border-color: #fb7185; }

.cell.general-in-check {
  background: radial-gradient(circle, #ff8989 0%, #dc2626 100%) !important;
  box-shadow: inset 0 0 0 3px #fecaca;
}

.general-in-check::after {
  content: '';
  position: absolute;
  inset: 3px;
  border: 3px solid #fff1f2;
  border-radius: 50%;
  pointer-events: none;
  animation: general-check-pulse 1.2s ease-out 3;
}

.cell.general-checkmated { box-shadow: inset 0 0 0 4px #fda4af; }
.general-danger {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: #881337;
  color: white;
  font-size: 12px;
  font-weight: 800;
  pointer-events: none;
}

@keyframes general-check-pulse {
  from { transform: scale(0.65); opacity: 1; }
  to { transform: scale(1.1); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .general-in-check::after { animation: none; }
}

.modal-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 20;
}

.modal-content {
  background: #1a1a2e;
  border: 1px solid rgba(233, 69, 96, 0.4);
  padding: 2rem;
  border-radius: 16px;
  text-align: center;
  max-width: 320px;
  width: 100%;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
}

.modal-content h3 {
  margin: 0 0 1.5rem 0;
  color: #e94560;
  font-size: 1.3rem;
}

.modal-content p { color: #e2e8f0; line-height: 1.5; margin: 0 0 1.5rem; }

.modal-actions {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.btn-primary {
  background: linear-gradient(135deg, #e94560, #c0392b);
  color: white;
  border: none;
  padding: 0.7rem;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 0.6rem;
  border-radius: 8px;
  cursor: pointer;
}

.hint {
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
}
</style>
