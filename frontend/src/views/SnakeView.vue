<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Phaser from 'phaser'
import { useRoute, useRouter } from 'vue-router'
import { SnakeScene } from '@/game/snake/SnakeScene'
import GlobalLeaderboard from '@/components/GlobalLeaderboard.vue'
import GameMinimap from '@/components/GameMinimap.vue'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, leaveRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameRoom } from '@/types/game'
import type { SnakeGameState } from '@/types/gameState'

const route = useRoute()
const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()

const roomId = route.params.roomId as string
const gameContainer = ref<HTMLDivElement | null>(null)
const roomInfo = ref<GameRoom | null>(null)
const gameState = ref<SnakeGameState | null>(null)
const score = ref(0)
const selectedColor = ref(localStorage.getItem('brim_snake_color') || '#38e88f')
const alive = ref(true)
const diedAt = ref(0)
const clock = ref(Date.now())
const myPlayerId = ref(session.player.id)
const cleanupFns = ref<Array<() => void>>([])

const isConnecting = ref(true)
const connectionError = ref<string | null>(null)
const gameStarted = ref(false)
const isGameOver = ref(false)
const gameOverMessage = ref('')

let game: Phaser.Game | null = null
let snakeScene: SnakeScene | null = null
let targetAngle = 0
let boosting = false
let lastInputSentAt = 0
let clockTimer: number | undefined

const respawnRemaining = computed(() => Math.max(0, Math.ceil((diedAt.value + 3000 - clock.value) / 1000)))


function initPhaser() {
  if (!gameContainer.value || game) return

  game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: gameContainer.value,
    width: gameContainer.value.clientWidth,
    height: gameContainer.value.clientHeight,
    backgroundColor: '#0f0f1e',
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH
    },
    scene: [SnakeScene]
  })

  game.events.on('ready', () => {
    const scene = game?.scene.getScene('SnakeScene') as SnakeScene
    if (scene) {
      scene.setPlayerId(myPlayerId.value)
      snakeScene = scene
      if (gameState.value) {
        snakeScene.updateGameState(gameState.value)
      }
    }
  })
}

function sendSteering(angle = targetAngle, boost = boosting, force = false) {
  const now = performance.now()
  if (!force && now - lastInputSentAt < 35) return
  targetAngle = angle
  boosting = boost
  lastInputSentAt = now
  stomp.publish(`/app/snake/${roomId}/move`, {
    type: MessageType.MOVE,
    roomId,
    playerId: session.player.id,
    payload: { angle: targetAngle, boosting }
  })
}

function handleRestart() {
  isGameOver.value = false
  gameStarted.value = false
  score.value = 0
  stomp.publish(`/app/snake/${roomId}/restart`, {
    type: MessageType.ACTION,
    roomId,
    playerId: session.player.id,
    payload: null
  })
}

function handleRespawn() {
  if (respawnRemaining.value > 0) return
  stomp.publish(`/app/snake/${roomId}/respawn`, {
    type: MessageType.ACTION, roomId, playerId: session.player.id, payload: null
  })
}

function updateColor() {
  localStorage.setItem('brim_snake_color', selectedColor.value)
  stomp.publish(`/app/snake/${roomId}/color`, {
    type: MessageType.ACTION, roomId, playerId: session.player.id, payload: { color: selectedColor.value }
  })
}

function goToRoom() {
  router.push(`/room/${roomId}`)
}

async function goToLobby() {
  try {
    await leaveRoom(roomId, session.player.id)
  } catch (e) {
    console.warn('Leave room error (non-fatal):', e)
  } finally {
    router.push('/')
  }
}

async function toggleFullscreen() {
  const arena = gameContainer.value?.parentElement
  if (!arena) return
  if (document.fullscreenElement) await document.exitFullscreen()
  else await arena.requestFullscreen()
}

onMounted(async () => {
  // Always initialize Phaser canvas right away so the user immediately sees the board
  initPhaser()
  clockTimer = window.setInterval(() => { clock.value = Date.now() }, 200)

  // Fetch room metadata
  try {
    const room = await getRoom(roomId)
    roomInfo.value = room
    if (room.status === 'FINISHED') {
      isGameOver.value = true
      gameOverMessage.value = 'Trận đấu này đã kết thúc!'
    }
  } catch (err) {
    console.warn('[SnakeView] Room fetch info:', err)
  }

  // Connect STOMP
  try {
    await stomp.connect()
    isConnecting.value = false

    // Subscribe to game state updates
    const unsubState = stomp.subscribe(`/topic/game/${roomId}/state`, (state: SnakeGameState) => {
      gameState.value = state
      snakeScene?.updateGameState(state)
      if (state.state === 'IN_PROGRESS' || Object.keys(state.snakes).length > 0) {
        gameStarted.value = true
        isGameOver.value = false
      }
      if (state.state === 'FINISHED') {
        isGameOver.value = true
        gameOverMessage.value = 'Trận đấu kết thúc!'
      }
      const my = state.snakes[myPlayerId.value]
      if (my) {
        score.value = my.score
        alive.value = my.alive
        diedAt.value = my.diedAt
      }
    })
    cleanupFns.value.push(unsubState)

    // Subscribe to room state updates
    const unsubRoom = stomp.subscribe(`/topic/room/${roomId}/state`, (room: GameRoom) => {
      roomInfo.value = room
      if (room.status === 'IN_PROGRESS') {
        gameStarted.value = true
        isGameOver.value = false
      } else if (room.status === 'FINISHED') {
        isGameOver.value = true
        gameOverMessage.value = 'Trận đấu kết thúc!'
      }
    })
    cleanupFns.value.push(unsubRoom)

    // Subscribe to game over
    const unsubOver = stomp.subscribe(`/topic/game/${roomId}/over`, () => {
      isGameOver.value = true
      gameOverMessage.value = 'Game Over! 🐍'
    })
    cleanupFns.value.push(unsubOver)

    // Join game
    stomp.publish(`/app/snake/${roomId}/join`, {
      type: MessageType.JOIN_ROOM,
      roomId,
      playerId: session.player.id,
      payload: {
        id: session.player.id,
        username: session.player.username,
        color: selectedColor.value,
        sessionId: session.player.sessionId,
        score: 0,
        ready: true,
        state: 'PLAYING'
      }
    })

    // Auto-ready trigger
    setTimeout(() => {
      stomp.publish(`/app/snake/${roomId}/ready`, {
        type: MessageType.JOIN_ROOM,
        roomId,
        playerId: session.player.id,
        payload: null
      })
    }, 400)

    const pointSnake = (clientX: number, clientY: number) => {
      const rect = game?.canvas.getBoundingClientRect()
      if (!rect) return
      const world = snakeScene?.screenToWorld(clientX, clientY, rect)
      const mySnake = gameState.value?.snakes[myPlayerId.value]
      const head = mySnake?.body[0]
      if (head && world) sendSteering(Math.atan2(world.y - head.y, world.x - head.x))
    }
    const onPointerMove = (e: PointerEvent) => pointSnake(e.clientX, e.clientY)
    const onPointerDown = (e: PointerEvent) => {
      if (e.button !== 0) return
      e.preventDefault()
      boosting = true
      pointSnake(e.clientX, e.clientY)
      sendSteering(targetAngle, true, true)
    }
    const onPointerUp = () => sendSteering(targetAngle, false, true)
    const onKeyDown = (e: KeyboardEvent) => {
      const angles: Record<string, number> = {
        ArrowRight: 0, d: 0, D: 0,
        ArrowDown: Math.PI / 2, s: Math.PI / 2, S: Math.PI / 2,
        ArrowLeft: Math.PI, a: Math.PI, A: Math.PI,
        ArrowUp: -Math.PI / 2, w: -Math.PI / 2, W: -Math.PI / 2
      }
      if (e.key in angles) {
        e.preventDefault()
        sendSteering(angles[e.key], boosting, true)
      } else if (e.code === 'Space') {
        e.preventDefault()
        sendSteering(targetAngle, true, true)
      }
    }
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.code === 'Space') sendSteering(targetAngle, false, true)
    }
    const canvas = gameContainer.value
    canvas?.addEventListener('pointermove', onPointerMove)
    canvas?.addEventListener('pointerdown', onPointerDown)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('keyup', onKeyUp)
    cleanupFns.value.push(() => {
      canvas?.removeEventListener('pointermove', onPointerMove)
      canvas?.removeEventListener('pointerdown', onPointerDown)
      window.removeEventListener('pointerup', onPointerUp)
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('keyup', onKeyUp)
    })

  } catch (err: any) {
    isConnecting.value = false
    connectionError.value = err.message || 'Không thể kết nối máy chủ'
    console.error('[SnakeView] Connection error:', err)
  }
})

onUnmounted(() => {
  cleanupFns.value.forEach((fn) => fn())
  game?.destroy(true)
  game = null
  snakeScene = null
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>

<template>
  <div class="snake-game-container">
    <!-- Header -->
    <header class="game-header">
      <div class="header-actions">
        <button class="btn-back" @click="goToLobby">← Về sảnh</button>
        <button class="btn-back" @click="toggleFullscreen">⛶ Toàn màn hình</button>
      </div>
      <div class="status-tags">
        <label class="color-picker" title="Màu rắn"><span>🎨</span><input v-model="selectedColor" type="color" @change="updateColor" /></label>
        <span class="badge score-badge">🎯 Điểm: {{ score }}</span>
        <span class="badge player-badge">👤 {{ session.player.username }}</span>
      </div>
    </header>

    <!-- Error state banner -->
    <div v-if="connectionError" class="alert-banner error">
      <span>❌ {{ connectionError }}</span>
      <button class="btn-sm" @click="goToLobby">Về trang chủ</button>
    </div>

    <!-- Canvas area wrapper -->
    <div class="canvas-wrapper">
      <div ref="gameContainer" class="game-canvas" />

      <GlobalLeaderboard game-type="SNAKE" class="global-rank-overlay" />
      <GameMinimap :state="gameState" game-type="SNAKE" :player-id="myPlayerId" />

      <!-- Loading / Connecting overlay -->
      <div v-if="isConnecting" class="canvas-overlay">
        <div class="spinner"></div>
        <p>Đang kết nối tới phòng chơi...</p>
      </div>

      <!-- Waiting to start overlay -->
      <div v-else-if="!gameStarted && !isGameOver" class="canvas-overlay">
        <div class="spinner"></div>
        <p>Đang chờ trận đấu bắt đầu...</p>
        <button class="btn-primary-action" @click="handleRestart">🚀 Bắt đầu ngay</button>
      </div>

      <!-- Game Over overlay -->
      <div v-if="isGameOver" class="canvas-overlay game-over">
        <div class="modal-card">
          <h2>{{ gameOverMessage || 'Game Over!' }}</h2>
          <p class="final-score">Điểm số của bạn: <strong>{{ score }}</strong></p>
          <div class="modal-buttons">
            <button class="btn-primary-action" @click="handleRestart">🎮 Chơi lại</button>
            <button class="btn-secondary-action" @click="goToRoom">🏠 Phòng chờ</button>
            <button class="btn-secondary-action" @click="goToLobby">📋 Về sảnh</button>
          </div>
        </div>
      </div>

      <div v-else-if="gameStarted && !alive" class="death-overlay">
        <div class="death-card">
          <span class="death-icon">☠</span>
          <h2>Rắn của bạn đã bị hạ</h2>
          <p>Điểm vòng này: <strong>{{ score }}</strong></p>
          <button :disabled="respawnRemaining > 0" @click="handleRespawn">
            {{ respawnRemaining > 0 ? `Hồi sinh sau ${respawnRemaining}s` : '⚡ Hồi sinh ngay' }}
          </button>
        </div>
      </div>
    </div>

    <div class="hint">
      🐍 Di chuột/chạm để đổi hướng • Giữ chuột, chạm hoặc <strong>SPACE</strong> để tăng tốc • Mồi càng lớn, điểm càng cao • Camera tự zoom theo kích thước rắn
    </div>

    <!-- Leaderboard -->
    <div class="leaderboard-card" v-if="gameState && Object.keys(gameState.snakes).length > 0">
      <h3>Bảng xếp hạng</h3>
      <div class="leaderboard-list">
        <div
          v-for="s in Object.values(gameState.snakes)"
          :key="s.playerId"
          class="leaderboard-item"
          :class="{ me: s.playerId === session.player.id, dead: !s.alive }"
        >
          <div class="player-label">
            <span class="avatar">{{ s.playerId.startsWith('bot-') ? '🤖' : '👤' }}</span>
            <span class="name">
              {{ s.playerId === session.player.id ? session.player.username + ' (Bạn)' : (s.username || (s.playerId.startsWith('bot-') ? 'Bot AI' : s.playerId.slice(0, 8))) }}
            </span>
          </div>
          <div class="player-stats">
            <span class="score-num">{{ s.score }} pts</span>
            <span class="life-status">{{ s.alive ? '🟢 Sống' : '☠️ Chết' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.snake-game-container {
  width: 100%;
  max-width: none;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
  margin: 0 auto;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  min-height: 100dvh;
  height: 100dvh;
  padding: .55rem clamp(.5rem, 2vw, 1.25rem);
  gap: .4rem;
  overflow: hidden;
}

.game-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
}

.header-actions { display: flex; gap: .35rem; }
.canvas-wrapper:fullscreen { width: 100vw; height: 100vh; min-height: 100vh; border: 0; border-radius: 0; }

.btn-back {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #fff;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
}

.game-header { margin-bottom: 0; min-height: 42px; }
.btn-back { padding: .35rem .7rem; font-size: .78rem; }

.btn-back:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: #e94560;
}

.status-tags {
  display: flex;
  gap: 0.75rem;
}

.badge {
  padding: 0.4rem 0.8rem;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 600;
}

.score-badge {
  background: rgba(46, 204, 113, 0.2);
  color: #2ecc71;
  border: 1px solid rgba(46, 204, 113, 0.4);
}

.player-badge {
  background: rgba(52, 152, 219, 0.2);
  color: #3498db;
  border: 1px solid rgba(52, 152, 219, 0.4);
}

.alert-banner {
  width: 100%;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-banner.error {
  background: rgba(231, 76, 60, 0.15);
  border: 1px solid rgba(231, 76, 60, 0.4);
  color: #e74c3c;
}

.btn-sm {
  background: #e74c3c;
  color: #fff;
  border: none;
  padding: 0.3rem 0.7rem;
  border-radius: 6px;
  cursor: pointer;
}

.canvas-wrapper {
  position: relative;
  width: 100%;
  height: auto;
  min-height: 0;
  flex: 1 1 auto;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
  border: 2px solid rgba(255, 255, 255, 0.1);
  background: #0f0f1e;
}

.global-rank-overlay { position: absolute; z-index: 8; top: 1rem; right: 1rem; pointer-events: none; }
.death-overlay { position: absolute; inset: 0; z-index: 12; display: grid; place-items: center; background: radial-gradient(circle, rgba(70,10,25,.42), rgba(4,7,18,.78)); backdrop-filter: blur(3px); }
.death-card { width: min(360px, 90%); padding: 1.6rem; text-align: center; border-radius: 20px; background: rgba(13,18,38,.93); border: 1px solid rgba(251,113,133,.35); box-shadow: 0 20px 60px rgba(0,0,0,.5); }
.death-icon { display: block; color: #fb7185; font-size: 3rem; line-height: 1; }
.death-card h2 { margin: .6rem 0 .2rem; }
.death-card p { color: #aab3c8; margin-bottom: 1rem; }
.death-card strong { color: #fbbf24; }
.death-card button { width: 100%; background: linear-gradient(135deg, #fb7185, #e94560); }

.game-canvas {
  width: 100%;
  height: 100%;
  cursor: crosshair;
  touch-action: none;
}

.canvas-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(15, 15, 30, 0.85);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  z-index: 10;
  color: #fff;
  text-align: center;
  padding: 1.5rem;
}

.canvas-overlay.game-over {
  background: rgba(15, 15, 30, 0.92);
}

.modal-card {
  background: #1a1a2e;
  border: 1px solid rgba(233, 69, 96, 0.4);
  padding: 2rem;
  border-radius: 16px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.6);
  max-width: 380px;
  width: 100%;
}

.modal-card h2 {
  margin: 0 0 0.5rem 0;
  color: #e94560;
  font-size: 1.6rem;
}

.final-score {
  font-size: 1.1rem;
  margin-bottom: 1.5rem;
  color: #ddd;
}

.final-score strong {
  color: #2ecc71;
  font-size: 1.4rem;
}

.modal-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.btn-primary-action {
  background: linear-gradient(135deg, #e94560, #c0392b);
  color: #fff;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary-action:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(233, 69, 96, 0.4);
}

.btn-secondary-action {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #fff;
  padding: 0.6rem 1.2rem;
  border-radius: 10px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary-action:hover {
  background: rgba(255, 255, 255, 0.15);
}

.spinner {
  width: 44px;
  height: 44px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #e94560;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.hint {
  color: #888;
  font-size: 0.85rem;
  text-align: center;
  font-size: .72rem;
  line-height: 1.25;
}

.leaderboard-card {
  width: 100%;
  background: #16213e;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  padding: 1rem;
  max-height: 132px;
  overflow: auto;
  flex-shrink: 0;
}

.leaderboard-card h3 {
  margin: 0 0 0.8rem 0;
  font-size: 0.95rem;
  color: #aaa;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.leaderboard-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.leaderboard-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0.8rem;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 8px;
  border: 1px solid transparent;
}

.leaderboard-item.me {
  border-color: #2ecc71;
  background: rgba(46, 204, 113, 0.1);
}

.leaderboard-item.dead {
  opacity: 0.5;
}

@media (max-width: 700px) {
  .canvas-wrapper { min-height: 0; height: auto; width: 100%; }
  .status-tags { gap: .35rem; }
  .badge { padding: .3rem .5rem; }
  .global-rank-overlay { transform: scale(.82); transform-origin: top right; }
}

.player-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.player-label .name {
  font-weight: 600;
  font-size: 0.9rem;
  color: #fff;
}

.player-stats {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.score-num {
  font-weight: 700;
  color: #2ecc71;
  font-size: 0.95rem;
}

.life-status {
  font-size: 0.8rem;
  color: #aaa;
}
</style>
