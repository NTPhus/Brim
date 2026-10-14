<script setup lang="ts">
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
const gameState = ref<ChessGameState | null>(null)
const roomInfo = ref<GameRoom | null>(null)
const selectedCell = ref<{ x: number; y: number } | null>(null)
const error = ref<string | null>(null)
const isGameOver = ref(false)
const gameOverMessage = ref('')
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
const lastMove = computed(() => gameState.value?.lastMove ?? null)

const legalTargets = computed(() => {
  if (!selectedCell.value) return new Set<string>()
  return new Set((gameState.value?.legalMoves ?? [])
    .filter(move => move.fromX === selectedCell.value!.x && move.fromY === selectedCell.value!.y)
    .map(move => `${move.toX},${move.toY}`))
})

watch(() => gameState.value?.currentPlayer, () => {
  selectedCell.value = null
})

const STATUS_TEXT = computed(() => {
  if (!gameState.value) return 'Đang kết nối...'
  if (isGameOver.value || gameState.value.status === 'FINISHED') return '🎉 Trò chơi kết thúc!'
  if (isMyTurn.value) return isCheck.value ? '⚠️ Chiếu tướng! Đến lượt bạn' : '🎯 Đến lượt bạn'
  return '⏳ Đợi đối thủ đi nước...'
})

function pieceKey(piece: ChessPiece): string {
  return `${piece.type}_${piece.color}`
}

// Keep White at the bottom of the board, as on a physical chessboard.
function boardY(row: number): number { return 8 - row }

function onCellClick(x: number, y: number) {
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
  isGameOver.value = false
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
      gameOverMessage.value = 'Trận đấu này đã kết thúc!'
    }
  } catch (e) {
    console.warn('[ChessView] Fetch room error:', e)
  }

  try {
    await stomp.connect()

    cleanupFns.value.push(
      stomp.subscribe(`/topic/game/${roomId}/state`, (state: ChessGameState) => {
        gameState.value = state
        if (state.status === 'FINISHED') {
          isGameOver.value = true
          gameOverMessage.value = state.winner
            ? (state.winner === myPlayerId
                ? 'Chúc mừng! Bạn đã thắng! 🏆'
                : `${roomInfo.value?.players.find(p => p.id === state.winner)?.username ?? state.winner} đã thắng! 🏆`)
            : 'Trận đấu kết thúc hòa!'
        } else {
          isGameOver.value = false
        }
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
      stomp.subscribe(`/topic/game/${roomId}/over`, (msg: any) => {
        isGameOver.value = true
        gameOverMessage.value = msg?.payload?.winner === myPlayerId ? 'Chúc mừng! Bạn đã thắng! 🏆' : 'Trò chơi kết thúc!'
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
      <div class="status-text" :class="{ mine: isMyTurn, check: isCheck }">{{ STATUS_TEXT }}</div>
      <div class="color-badge" v-if="myColor">
        Bạn là quân: <strong>{{ myColor === 'WHITE' ? 'Trắng ⚪' : 'Đen ⚫' }}</strong>
      </div>
    </div>

    <div v-if="error" class="error-msg">⚠️ {{ error }}</div>

    <div class="board-wrapper">
      <div class="board-container">
        <div class="board">
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
                capture: legalTargets.has(`${col - 1},${boardY(row)}`) && !!pieceAt[`${col - 1},${boardY(row)}`]
              }]"
              @click="onCellClick(col - 1, boardY(row))"
            >
              <span
                v-if="pieceAt[`${col - 1},${boardY(row)}`]"
                class="piece"
                :class="pieceAt[`${col - 1},${boardY(row)}`]?.color === 'WHITE' ? 'white' : 'black'"
              >
                {{ PIECE_SYMBOLS[pieceKey(pieceAt[`${col - 1},${boardY(row)}`]!)] }}
              </span>
              <span v-if="selectedCell?.x === col - 1 && selectedCell?.y === boardY(row)" class="selected-ring" />
              <span v-else-if="legalTargets.has(`${col - 1},${boardY(row)}`)" class="move-preview" />
            </div>
          </div>
        </div>

        <aside class="history" v-if="gameState?.moveHistory?.length">
          <h4>Lịch sử nước đi</h4>
          <div class="move-list">
            <span v-for="(move, i) in gameState.moveHistory" :key="i" class="move-item">{{ i + 1 }}. {{ move }}</span>
          </div>
        </aside>

        <div v-if="isMyTurn && !isCheck" class="move-hint">
          💡 Nhấp vào quân của bạn để chọn, sau đó nhấp vào ô muốn đi
        </div>

        <div v-if="isCheck" class="check-warning">
          ⚠️ Chiếu tướng! Hãy cẩn thận nước đi tiếp theo
        </div>
      </div>

      <!-- Game Over Modal Overlay -->
      <div v-if="isGameOver" class="modal-overlay">
        <div class="modal-content">
          <h3>{{ gameOverMessage || 'Ván cờ kết thúc!' }}</h3>
          <div class="modal-actions">
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

.check-warning {
  margin-top: 0.8rem;
  color: var(--danger);
  font-weight: 600;
  animation: pulse 1.5s infinite;
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

.move-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  max-height: 100px;
  overflow-y: auto;
}

.move-item {
  background: rgba(255, 255, 255, 0.05);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-family: monospace;
}
</style>
