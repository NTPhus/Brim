<script setup lang="ts">
import { useChessMotion } from '@/composables/useChessMotion'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, leaveRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameRoom } from '@/types/game'
import type { ChessGameState, ChessPiece, ChessMove } from '@/types/gameState'

const route = useRoute()
const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()

const roomId = route.params.roomId as string
const myPlayerId = session.player.id
const motionBoard=ref<HTMLElement|null>(null)
const gameState = ref<ChessGameState | null>(null)
useChessMotion(motionBoard,gameState,true)
const roomInfo = ref<GameRoom | null>(null)
const selectedCell = ref<{ x: number; y: number } | null>(null)
const error = ref<string | null>(null)
const isGameOver = ref(false)
const showResult = ref(true)
const cleanupFns = ref<Array<() => void>>([])

const PIECE_SYMBOLS: Record<string, string> = {
  KING_WHITE: '♔', QUEEN_WHITE: '♕', ROOK_WHITE: '♖',
  BISHOP_WHITE: '♗', KNIGHT_WHITE: '♘', PAWN_WHITE: '♙',
  KING_BLACK: '♚', QUEEN_BLACK: '♛', ROOK_BLACK: '♜',
  BISHOP_BLACK: '♝', KNIGHT_BLACK: '♞', PAWN_BLACK: '♟'
}

const pieceAt = computed(() => {
  const map: Record<string, ChessPiece> = {}
  if (gameState.value?.pieces) {
    for (const piece of Object.values(gameState.value.pieces)) {
      map[`${piece.x},${piece.y}`] = piece
    }
  }
  return map
})

const myColor = computed(() => {
  if (!gameState.value) return null
  if (gameState.value.whitePlayer === myPlayerId) return 'WHITE'
  if (gameState.value.blackPlayer === myPlayerId) return 'BLACK'
  return null
})

const isMyTurn = computed(() => {
  if (!gameState.value) return false
  return gameState.value.currentPlayer === myPlayerId
})

const isCheck = computed(() => {
  if (!gameState.value) return false
  return gameState.value.check ?? false
})
const isCheckmate = computed(() => gameState.value?.checkmate
  ?? (gameState.value?.status === 'FINISHED' && isCheck.value))
const checkedColor = computed(() => {
  const state = gameState.value
  if (!state || !isCheck.value) return null
  return state.currentPlayer === state.whitePlayer ? 'WHITE' : 'BLACK'
})
const checkedKingSquare = computed(() => {
  const king = Object.values(pieceAt.value).find(piece => piece.type === 'KING' && piece.color === checkedColor.value)
  return king ? `${king.x},${king.y}` : null
})
const checkMessage = computed(() => {
  const side = checkedColor.value === 'WHITE' ? 'Trắng' : 'Đen'
  if (isCheckmate.value) return `Chiếu hết! Vua ${side} không còn nước thoát.`
  return isMyTurn.value
    ? 'Bạn đang bị chiếu! Hãy di chuyển vua, che chắn hoặc bắt quân đang chiếu.'
    : `Bạn đang chiếu vua ${side}. Đối thủ phải hóa giải nước chiếu.`
})
const gameOverMessage = computed(() => {
  const state = gameState.value
  if (state?.status === 'DRAW') return 'Hòa cờ — hết nước đi hợp lệ nhưng vua không bị chiếu.'
  if (!state?.winner) return 'Ván cờ kết thúc!'
  if (state.winner === myPlayerId) return 'Bạn đã thắng! 🏆'
  const name = roomInfo.value?.players.find(p => p.id === state.winner)?.username ?? 'Đối thủ'
  return `${name} đã thắng. Bạn đã thua ván này.`
})
const lastMove = computed(() => gameState.value?.lastMove ?? null)
const historyRows = computed(() => {
  const moves = gameState.value?.moveHistory ?? []
  return Array.from({ length: Math.ceil(moves.length / 2) }, (_, index) => ({
    number: index + 1,
    white: formatMove(moves[index * 2]),
    black: formatMove(moves[index * 2 + 1])
  }))
})

const legalTargets = computed(() => {
  if (!selectedCell.value) return new Set<string>()
  return new Set((gameState.value?.legalMoves ?? [])
    .filter(move => move.fromX === selectedCell.value!.x && move.fromY === selectedCell.value!.y)
    .map(move => `${move.toX},${move.toY}`))
})

watch(() => gameState.value?.currentPlayer, () => {
  selectedCell.value = null
})

watch(() => gameState.value?.status, () => {
  showResult.value = true
  selectedCell.value = null
})

const STATUS_TEXT = computed(() => {
  if (!gameState.value) return 'Đang kết nối...'
  if (isCheckmate.value) return '♚ Chiếu hết — Checkmate!'
  if (gameState.value.status === 'DRAW') return '🤝 Hòa cờ — Stalemate'
  if (isGameOver.value || gameState.value.status === 'FINISHED') return 'Ván cờ kết thúc!'
  if (isMyTurn.value) return isCheck.value ? '⚠️ Chiếu tướng! Đến lượt bạn' : '🎯 Đến lượt bạn'
  if (isCheck.value) return '♔ Bạn đang chiếu tướng!'
  return '⏳ Đợi đối thủ đi nước...'
})

function pieceKey(piece: ChessPiece): string {
  return `${piece.type}_${piece.color}`
}

// Keep White at the bottom of the board, as on a physical chessboard.
function boardY(row: number): number { return 8 - row }

function formatMove(raw?: string): string {
  if (!raw) return '—'
  const match = raw.match(/fromX=(\d+), fromY=(\d+), toX=(\d+), toY=(\d+)/)
  if (!match) return raw.replace(/^ChessMove\[|\]$/g, '')
  const [, fx, fy, tx, ty] = match.map(Number)
  return `${String.fromCharCode(97 + fx)}${8 - fy} → ${String.fromCharCode(97 + tx)}${8 - ty}`
}

function onCellClick(x: number, y: number) {
  if (!isMyTurn.value || isGameOver.value || gameState.value?.status !== 'IN_PROGRESS') return
  error.value = null
  const piece = pieceAt.value[`${x},${y}`]

  if (selectedCell.value) {
    // If clicking same piece, deselect
    if (selectedCell.value.x === x && selectedCell.value.y === y) {
      selectedCell.value = null
      return
    }

    // Clicking another own piece changes the preview selection.
    if (piece?.color === myColor.value) {
      selectedCell.value = { x, y }
      return
    }

    if (!legalTargets.value.has(`${x},${y}`)) {
      error.value = 'Ô này không phải là nước đi hợp lệ'
      return
    }

    // Make a move only after choosing one of the previewed squares.
    const move: ChessMove = {
      fromX: selectedCell.value.x,
      fromY: selectedCell.value.y,
      toX: x,
      toY: y
    }
    stomp.publish(`/app/turn/${roomId}/move`, {
      type: MessageType.MAKE_MOVE,
      roomId,
      playerId: myPlayerId,
      payload: move,
      timestamp: Date.now()
    })
    selectedCell.value = null
    return
  }

  // Select own piece
  if (piece && isMyTurn.value) {
    // Verify it's player's piece if color is assigned
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

onMounted(async () => {
  try {
    const room = await getRoom(roomId)
    roomInfo.value = room
    if (room.status === 'FINISHED') {
      isGameOver.value = true
    }
  } catch (e) {
    console.warn('[ChessView] Fetch room error:', e)
  }

  try {
    await stomp.connect()

    cleanupFns.value.push(
      stomp.subscribe(`/topic/game/${roomId}/state`, (state: ChessGameState) => {
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
      stomp.subscribe(`/topic/game/${roomId}/over`, (msg: { payload?: { finalState?: ChessGameState } }) => {
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
</script>

<template>
  <div class="chess-game">
    <div class="game-header">
      <button class="secondary" @click="handleGoToLobby">← Về sảnh</button>
      <div class="status-text" :class="{ mine: isMyTurn && !isGameOver, check: isCheck }" role="status" aria-live="polite">{{ STATUS_TEXT }}</div>
      <div class="color-badge" v-if="myColor">
        Bạn là quân: <strong>{{ myColor === 'WHITE' ? 'Trắng ⚪' : 'Đen ⚫' }}</strong>
      </div>
    </div>

    <div v-if="error" class="error-msg">⚠️ {{ error }}</div>

    <div v-if="isCheck" class="check-banner" :class="{ checkmate: isCheckmate }" role="status" aria-live="polite">
      <span class="check-icon" aria-hidden="true">{{ isCheckmate ? '♚' : '⚠' }}</span>
      <div>
        <strong>{{ isCheckmate ? 'CHIẾU HẾT · CHECKMATE' : 'CHIẾU TƯỚNG · CHECK' }}</strong>
        <p>{{ checkMessage }}</p>
      </div>
    </div>
    <button v-if="isGameOver && !showResult" class="btn-secondary" @click="showResult = true">Xem kết quả</button>

    <div class="board-wrapper">
      <div class="board-container">
        <div ref="motionBoard" class="board">
          <div
            v-for="row in 8"
            :key="`r${row}`"
            class="board-row"
          >
            <div
              v-for="col in 8"
              :key="`c${col}`"
              :class="[(row + col) % 2 === 0 ? 'light' : 'dark', {
                selected: selectedCell?.x === col - 1 && selectedCell?.y === boardY(row),
                'last-from': lastMove?.fromX === col - 1 && lastMove?.fromY === boardY(row),
                'last-to': lastMove?.toX === col - 1 && lastMove?.toY === boardY(row),
                'legal-target': legalTargets.has(`${col - 1},${boardY(row)}`),
                'king-in-check': checkedKingSquare === `${col - 1},${boardY(row)}`,
                'king-checkmated': isCheckmate && checkedKingSquare === `${col - 1},${boardY(row)}`,
                capture: legalTargets.has(`${col - 1},${boardY(row)}`) && !!pieceAt[`${col - 1},${boardY(row)}`]
              }]"
              :title="checkedKingSquare === `${col - 1},${boardY(row)}` ? (isCheckmate ? 'Vua bị chiếu hết' : 'Vua đang bị chiếu') : undefined"
              @click="onCellClick(col - 1, boardY(row))"
            >
              <span
                v-if="pieceAt[`${col - 1},${boardY(row)}`]"
                class="piece" :data-motion-key="`${col - 1},${boardY(row)}`"
                :class="pieceAt[`${col - 1},${boardY(row)}`]?.color === 'WHITE' ? 'white' : 'black'"
              >
                {{ PIECE_SYMBOLS[pieceKey(pieceAt[`${col - 1},${boardY(row)}`]!)] }}
              </span>
              <span v-if="checkedKingSquare === `${col - 1},${boardY(row)}`" class="king-danger" aria-hidden="true">!</span>
              <span v-if="selectedCell?.x === col - 1 && selectedCell?.y === boardY(row)" class="selected-ring" />
              <span v-else-if="legalTargets.has(`${col - 1},${boardY(row)}`)" class="move-preview" />
            </div>
          </div>
        </div>

        <aside class="history" v-if="gameState?.moveHistory?.length">
          <h4>Lịch sử nước đi</h4>
          <div class="history-columns"><span>#</span><span>Trắng</span><span>Đen</span></div>
          <div class="move-list">
            <div v-for="row in historyRows" :key="row.number" class="move-row">
              <b>{{ row.number }}</b><span>{{ row.white }}</span><span>{{ row.black }}</span>
            </div>
          </div>
        </aside>

        <div v-if="isMyTurn && !isCheck" class="move-hint">
          💡 Nhấp vào quân của bạn để chọn, sau đó nhấp vào ô muốn đi
        </div>

      </div>

      <!-- Game Over Modal Overlay -->
      <div v-if="isGameOver && showResult" class="modal-overlay">
        <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="chess-result-title" aria-describedby="chess-result-detail">
          <h3 id="chess-result-title">{{ isCheckmate ? '♚ Chiếu hết — Checkmate!' : gameState?.status === 'DRAW' ? '🤝 Hòa cờ' : 'Ván cờ kết thúc' }}</h3>
          <p id="chess-result-detail">{{ gameOverMessage }}</p>
          <div class="modal-actions">
            <button class="btn-secondary" autofocus @click="showResult = false">Xem bàn cờ</button>
            <button class="btn-primary" @click="handleRestart">🎮 Chơi lại</button>
            <button class="btn-secondary" @click="router.push(`/room/${roomId}`)">🏠 Phòng chờ</button>
            <button class="btn-secondary" @click="handleGoToLobby">📋 Về sảnh</button>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.chess-game {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  width: 100%;
  max-width: 920px;
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

.status-text {
  font-weight: 600;
  padding: 0.4rem 1rem;
  border-radius: 999px;
  background: var(--bg-card);
  font-size: 0.9rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.status-text.mine {
  color: var(--success);
  background: rgba(46, 204, 113, 0.15);
  border-color: rgba(46, 204, 113, 0.4);
}

.status-text.check {
  color: var(--danger);
  background: rgba(231, 76, 60, 0.15);
  box-shadow: 0 0 10px rgba(231, 76, 60, 0.3);
}

.color-badge {
  font-size: 0.85rem;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 0.3rem 0.7rem;
  border-radius: 8px;
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
  gap: 1rem;
  align-items: flex-start;
}

.board-container {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 210px;
  gap: 0.8rem;
  align-items: center;
}

.board-container > .board { grid-column: 1; }
.board-container > .history { grid-column: 2; grid-row: 1 / span 3; }

.board {
  display: flex;
  flex-direction: column;
  border: 4px solid #5d4037;
  border-radius: 6px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.board-row {
  display: flex;
}

.board-row > div {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  user-select: none;
}

.board-row > div.light {
  background-color: #f0d9b5;
}

.board-row > div.dark {
  background-color: #b58863;
}

.board-row > div.selected {
  background-color: #7fa650 !important;
}

.board-row > div.last-from { box-shadow: inset 0 0 0 4px rgba(251, 191, 36, .55); }
.board-row > div.last-to { background-color: rgba(250, 204, 21, .68) !important; }

.board-row > div.legal-target { background-color: #a9c96b !important; }

.move-preview {
  position: absolute;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(20, 85, 35, 0.55);
  pointer-events: none;
}

.capture .move-preview {
  width: 46px;
  height: 46px;
  border: 4px solid rgba(20, 85, 35, 0.72);
  background: transparent;
}

.piece {
  font-size: 2.4rem;
  line-height: 1;
}

.piece.white {
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7), 0 0 1px #000;
}

.piece.black {
  color: #1a1a1a;
  text-shadow: 0 0 1px rgba(255, 255, 255, 0.4);
}

.selected-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 3px solid #2ecc71;
  pointer-events: none;
}

.move-hint {
  margin-top: 0.8rem;
  color: var(--text-muted);
  font-size: 0.85rem;
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

.board-row > div.king-in-check {
  background: radial-gradient(circle, #ff8989 0%, #dc2626 100%) !important;
  box-shadow: inset 0 0 0 3px #fecaca;
}

.king-in-check::after {
  content: '';
  position: absolute;
  inset: 3px;
  border: 3px solid #fff1f2;
  border-radius: 50%;
  pointer-events: none;
  animation: king-check-pulse 1.2s ease-out 3;
}

.board-row > div.king-checkmated { box-shadow: inset 0 0 0 4px #fda4af; }
.king-danger {
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

@keyframes king-check-pulse {
  from { transform: scale(0.65); opacity: 1; }
  to { transform: scale(1.1); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .king-in-check::after { animation: none; }
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

.history {
  width: 210px;
  background: var(--bg-card);
  padding: 1rem;
  border-radius: 12px;
  border: 1px solid var(--border);
}

@media (max-width: 760px) {
  .board-container { grid-template-columns: 1fr; }
  .board-container > .history { grid-column: 1; grid-row: auto; width: 100%; }
  .board-row > div { width: min(11vw, 56px); height: min(11vw, 56px); }
}

.history h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.history-columns { display: grid; grid-template-columns: 24px 1fr 1fr; gap: .35rem; padding: 0 .35rem .25rem; color: var(--text-muted); font-size: .65rem; text-transform: uppercase; letter-spacing: .04em; }

.move-list {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-height: 360px;
  overflow-y: auto;
}

.move-row { display: grid; grid-template-columns: 24px 1fr 1fr; gap: .35rem; align-items: center; padding: .32rem .35rem; border-radius: 6px; background: rgba(255,255,255,.045); font-size: .76rem; }
.move-row b { color: #fbbf24; }
.move-row span { color: var(--text-secondary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; white-space: nowrap; }

.move-item {
  background: rgba(255, 255, 255, 0.05);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-family: monospace;
}
</style>
