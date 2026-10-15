<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import Phaser from 'phaser'
import { useRoute, useRouter } from 'vue-router'
import { TankScene } from '@/game/tank/TankScene'
import GlobalLeaderboard from '@/components/GlobalLeaderboard.vue'
import GameMinimap from '@/components/GameMinimap.vue'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, leaveRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameRoom } from '@/types/game'
import type { TankGameState } from '@/types/gameState'

const route = useRoute()
const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()

const roomId = route.params.roomId as string
const gameContainer = ref<HTMLDivElement | null>(null)
const gameState = ref<TankGameState | null>(null)
const roomInfo = ref<GameRoom | null>(null)
const health = ref(100)
const score = ref(0)
const selectedColor = ref(localStorage.getItem('brim_tank_color') || '#2ecc71')
const alive = ref(true)
const diedAt = ref(0)
const clock = ref(Date.now())
const lastSnapshotAt = ref(Date.now())
const networkStatus = computed(() => !stomp.connected.value ? 'Đang kết nối lại…' : clock.value - lastSnapshotAt.value > 1000 ? 'Mạng chậm · đang chờ cập nhật' : 'Đã kết nối')
const myPlayerId = session.player.id

const isConnecting = ref(true)
const isGameOver = ref(false)
const gameOverMessage = ref('')
const gameStarted = ref(false)

const input = ref({
  forward: false,
  backward: false,
  turnLeft: false,
  turnRight: false,
  shoot: false
})

let game: Phaser.Game | null = null
let tankScene: TankScene | null = null
const pressedKeys = new Set<string>()
const cleanupFns = ref<Array<() => void>>([])
let clockTimer: number | undefined

const activeEffects = computed(() => {
  const tank = gameState.value?.tanks[myPlayerId]
  return [
    { label: 'Khiên', until: tank?.shieldUntil ?? 0 },
    { label: 'Tăng tốc', until: tank?.speedUntil ?? 0 },
    { label: 'Bắn nhanh', until: tank?.rapidFireUntil ?? 0 }
  ].filter(effect => effect.until > clock.value)
})

const respawnRemaining = computed(() => Math.max(0, Math.ceil((diedAt.value + 3000 - clock.value) / 1000)))

function sendInput() {
  stomp.publish(`/app/tank/${roomId}/input`, {
    type: MessageType.ACTION,
    roomId,
    playerId: session.player.id,
    payload: {
      movingForward: input.value.forward,
      movingBackward: input.value.backward,
      turningLeft: input.value.turnLeft,
      turningRight: input.value.turnRight,
      shooting: input.value.shoot
    }
  })
}

function handleRestart() {
  isGameOver.value = false
  alive.value = true
  health.value = 100
  score.value = 0
  stomp.publish(`/app/tank/${roomId}/restart`, {
    type: MessageType.ACTION,
    roomId,
    playerId: session.player.id,
    payload: null
  })
}

function handleRespawn() {
  if (respawnRemaining.value > 0) return
  stomp.publish(`/app/tank/${roomId}/respawn`, {
    type: MessageType.ACTION, roomId, playerId: session.player.id, payload: null
  })
}

function updateColor() {
  localStorage.setItem('brim_tank_color', selectedColor.value)
  stomp.publish(`/app/tank/${roomId}/color`, {
    type: MessageType.ACTION, roomId, playerId: session.player.id, payload: { color: selectedColor.value }
  })
}

async function handleGoToLobby() {
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

function initPhaser() {
  if (!gameContainer.value || game) return

  game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: gameContainer.value,
    width: gameContainer.value.clientWidth,
    height: gameContainer.value.clientHeight,
    backgroundColor: '#1a1a2e',
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH
    },
    scene: [TankScene]
  })

  game.events.on('ready', () => {
    const scene = game?.scene.getScene('TankScene') as TankScene
    if (scene) {
      scene.setPlayerId(myPlayerId)
      tankScene = scene
      if (gameState.value) {
        tankScene.updateGameState(gameState.value)
      }
    }
  })
}

onMounted(async () => {
  // Mount Phaser immediately
  initPhaser()
  clockTimer = window.setInterval(() => {
    clock.value = Date.now()
    if (stomp.connected.value && alive.value) sendInput()
  }, 100)

  // Fetch room metadata
  try {
    const room = await getRoom(roomId)
    roomInfo.value = room
    if (room.status === 'FINISHED') {
      isGameOver.value = true
      gameOverMessage.value = 'Trận chiến xe tăng đã kết thúc!'
    }
  } catch (e) {
    console.warn('[TankView] Fetch room error:', e)
  }

  try {
    await stomp.connect()
    isConnecting.value = false

    const unsubState = stomp.subscribe(`/topic/game/${roomId}/state/${session.player.id}`, (state: TankGameState) => {
      if ((state.serverTime ?? 0) < (gameState.value?.serverTime ?? 0)) return
      lastSnapshotAt.value = Date.now()
      gameState.value = state
      tankScene?.updateGameState(state)
      if (state.state === 'IN_PROGRESS' || Object.keys(state.tanks).length > 0) {
        gameStarted.value = true
        isGameOver.value = false
      }
      if (state.state === 'FINISHED') {
        isGameOver.value = true
        gameOverMessage.value = 'Trận chiến kết thúc!'
      }
      const myTank = state.tanks[myPlayerId]
      if (myTank) {
        health.value = myTank.health
        score.value = myTank.score
        alive.value = myTank.alive
        diedAt.value = myTank.diedAt
      }
    })
    cleanupFns.value.push(unsubState)

    const unsubRoom = stomp.subscribe(`/topic/room/${roomId}/state`, (room: GameRoom) => {
      roomInfo.value = room
      if (room.status === 'FINISHED') {
        isGameOver.value = true
      }
    })
    cleanupFns.value.push(unsubRoom)

    const unsubOver = stomp.subscribe(`/topic/game/${roomId}/over`, () => {
      isGameOver.value = true
      gameOverMessage.value = 'Chiến dịch kết thúc! 💥'
    })
    cleanupFns.value.push(unsubOver)
    cleanupFns.value.push(stomp.subscribe(`/topic/game/${roomId}/error`, (error: { message: string }) => {
      isGameOver.value = true
      gameOverMessage.value = error.message
    }))

    stomp.publish(`/app/tank/${roomId}/join`, {
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

    setTimeout(() => {
      stomp.publish(`/app/tank/${roomId}/ready`, {
        type: MessageType.JOIN_ROOM,
        roomId,
        playerId: session.player.id,
        payload: null
      })
    }, 400)

    const onKeyDown = (e: KeyboardEvent) => {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space'].includes(e.code)) {
        e.preventDefault()
      }
      if (pressedKeys.has(e.code)) return
      pressedKeys.add(e.code)
      updateInputState()
    }

    const onKeyUp = (e: KeyboardEvent) => {
      pressedKeys.delete(e.code)
      updateInputState()
    }

    function updateInputState() {
      input.value.forward = pressedKeys.has('KeyW') || pressedKeys.has('ArrowUp')
      input.value.backward = pressedKeys.has('KeyS') || pressedKeys.has('ArrowDown')
      input.value.turnLeft = pressedKeys.has('KeyA') || pressedKeys.has('ArrowLeft')
      input.value.turnRight = pressedKeys.has('KeyD') || pressedKeys.has('ArrowRight')
      input.value.shoot = pressedKeys.has('Space')
      sendInput()
    }

    const releaseInput = () => { pressedKeys.clear(); updateInputState() }
    window.addEventListener('blur', releaseInput)
    cleanupFns.value.push(() => window.removeEventListener('blur', releaseInput))
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('keyup', onKeyUp)
    cleanupFns.value.push(() => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('keyup', onKeyUp)
    })

  } catch (err: any) {
    isConnecting.value = false
    console.error('[TankView] Stomp error:', err)
  }
})

onUnmounted(() => {
  cleanupFns.value.forEach((fn) => fn())
  game?.destroy(true)
  game = null
  tankScene = null
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>

<template>
  <div class="tank-game">
    <div class="network-strip" role="status">{{ networkStatus }} · Art: Kenney / CC0</div>
    <div class="item-legend">✚ Hồi máu · ◯ Khiên 1 phát / 10s · » Tăng tốc 6s · » Bắn nhanh 6s
      <strong v-for="effect in activeEffects" :key="effect.label">{{ effect.label }} {{ Math.ceil((effect.until - clock) / 1000) }}s</strong>
    </div>
    <div class="game-header">
      <div class="header-actions">
        <button class="secondary" @click="handleGoToLobby">← Về sảnh</button>
        <button class="secondary" @click="toggleFullscreen">⛶ Toàn màn hình</button>
      </div>
      <div class="game-info">
        <label class="color-picker" title="Màu xe tăng"><span>🎨</span><input v-model="selectedColor" type="color" @change="updateColor" /></label>
        <span class="hp" :class="{ low: health <= 30 }">❤️ {{ health }} HP</span>
        <span class="score">⭐ {{ score }} điểm</span>
        <span v-if="!alive" class="dead">Đã hy sinh ☠️</span>
      </div>
    </div>

    <div class="hint">
      🎮 <strong>W / ⬆️</strong> Tiến • <strong>S / ⬇️</strong> Lùi • <strong>A / ⬅️</strong> Rẽ trái • <strong>D / ➡️</strong> Rẽ phải • <strong>SPACE</strong> Bắn đạn
    </div>

    <div class="canvas-wrapper">
      <div ref="gameContainer" class="game-canvas" />

      <GlobalLeaderboard game-type="TANK" class="global-rank-overlay" />
      <GameMinimap :state="gameState" game-type="TANK" :player-id="myPlayerId" />

      <!-- Connecting overlay -->
      <div v-if="isConnecting" class="modal-overlay">
        <div class="spinner"></div>
        <p>Đang chuẩn bị chiến trường...</p>
      </div>

      <!-- Game Over overlay -->
      <div v-if="isGameOver" class="modal-overlay">
        <div class="modal-card">
          <h2>{{ gameOverMessage || 'Trận chiến kết thúc!' }}</h2>
          <p class="final-score">Tổng điểm: <strong>{{ score }}</strong></p>
          <div class="modal-buttons">
            <button class="btn-primary" @click="handleRestart">🎮 Chơi lại</button>
            <button class="btn-secondary" @click="router.push(`/room/${roomId}`)">🏠 Phòng chờ</button>
            <button class="btn-secondary" @click="handleGoToLobby">📋 Về sảnh</button>
          </div>
        </div>
      </div>

      <div v-else-if="gameStarted && !alive" class="death-overlay">
        <div class="death-card">
          <span class="death-icon">💥</span>
          <h2>Xe tăng đã bị phá hủy</h2>
          <p>Bạn vẫn giữ <strong>{{ score }} điểm</strong></p>
          <button :disabled="respawnRemaining > 0" @click="handleRespawn">
            {{ respawnRemaining > 0 ? `Hồi sinh sau ${respawnRemaining}s` : '🛡️ Trở lại chiến trường' }}
          </button>
        </div>
      </div>
    </div>

    <div class="leaderboard" v-if="gameState && Object.keys(gameState.tanks).length > 0">
      <h4>Chiến binh trong trận</h4>
      <div class="leaderboard-grid">
        <div
          v-for="tank in Object.values(gameState.tanks)"
          :key="tank.playerId"
          class="lb-row"
          :class="{ me: tank.playerId === myPlayerId, dead: !tank.alive }"
        >
          <span class="lb-player">
            {{ tank.playerId.startsWith('bot-') ? '🤖 Bot AI' : (tank.playerId === myPlayerId ? session.player.username + ' (Bạn)' : tank.playerId.slice(0, 8)) }}
          </span>
          <span class="lb-hp">❤️ {{ tank.health }} HP</span>
          <span class="lb-score">⭐ {{ tank.score }} điểm</span>
          <span class="lb-status">{{ tank.alive ? '🟢 Chiến đấu' : '☠️ Hy sinh' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.network-strip { display: flex; justify-content: space-between; gap: 1rem; padding: .35rem 1rem; font-size: .75rem; color: #bbdccb; background: #17261f; border-bottom: 1px solid #34523f; }
.network-strip span { color: #91a89a; }
.item-legend { padding: .45rem 1rem; color: #bad2c2; background: #112019; font-size: .75rem; }
.item-legend strong { display: inline-block; margin-left: .75rem; color: #f7dc86; }

.tank-game {
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
  flex-wrap: wrap;
  gap: 0.5rem;
}

.header-actions { display: flex; gap: .35rem; }
.game-header { margin-bottom: 0; min-height: 42px; }
.header-actions .secondary { padding: .35rem .7rem; font-size: .78rem; }
.canvas-wrapper:fullscreen { width: 100vw; height: 100vh; min-height: 100vh; border: 0; border-radius: 0; }

.game-info {
  display: flex;
  gap: .45rem;
  font-weight: 600;
  align-items: center;
}

.hp {
  color: var(--success);
  font-size: .82rem;
  padding: 0.3rem 0.55rem;
  background: var(--bg-input);
  border-radius: 20px;
  border: 1px solid rgba(46, 204, 113, 0.4);
}

.hp.low {
  color: var(--danger);
  background: rgba(231, 76, 60, 0.15);
  border-color: rgba(231, 76, 60, 0.4);
}

.score {
  color: var(--warning);
  font-size: .82rem;
  padding: 0.3rem 0.55rem;
  background: var(--bg-input);
  border-radius: 20px;
  border: 1px solid rgba(243, 156, 18, 0.4);
}

.dead {
  color: var(--danger);
  font-size: 0.9rem;
}

.hint {
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  font-size: .72rem;
  line-height: 1.25;
}

.canvas-wrapper {
  position: relative;
  width: 100%;
  height: auto;
  min-height: 0;
  flex: 1 1 auto;
  border-radius: 16px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
  background: #1a1a2e;
}

.global-rank-overlay { position: absolute; z-index: 8; top: 1rem; right: 1rem; pointer-events: none; }
.death-overlay { position: absolute; inset: 0; z-index: 12; display: grid; place-items: center; background: radial-gradient(circle, rgba(90,45,5,.35), rgba(4,7,18,.82)); backdrop-filter: blur(3px); }
.death-card { width: min(370px, 90%); padding: 1.7rem; text-align: center; border-radius: 20px; background: rgba(13,18,38,.94); border: 1px solid rgba(251,191,36,.32); box-shadow: 0 20px 60px rgba(0,0,0,.55); }
.death-icon { font-size: 3rem; }
.death-card h2 { margin: .45rem 0 .25rem; }
.death-card p { color: #aab3c8; margin-bottom: 1rem; }
.death-card strong { color: #fbbf24; }
.death-card button { width: 100%; background: linear-gradient(135deg, #f59e0b, #ef4444); }

@media (max-width: 700px) {
  .canvas-wrapper { width: 100%; height: auto; min-height: 0; }
  .global-rank-overlay { transform: scale(.82); transform-origin: top right; }
}

.game-canvas {
  width: 100%;
  height: 100%;
}

.modal-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(15, 15, 30, 0.88);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 10;
  color: #fff;
  padding: 1.5rem;
}

.modal-card {
  background: #1a1a2e;
  border: 1px solid rgba(233, 69, 96, 0.4);
  padding: 2rem;
  border-radius: 16px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.6);
  max-width: 360px;
  width: 100%;
  text-align: center;
}

.modal-card h2 {
  margin: 0 0 0.5rem 0;
  color: #e94560;
  font-size: 1.5rem;
}

.final-score {
  font-size: 1.1rem;
  margin-bottom: 1.5rem;
  color: #ddd;
}

.final-score strong {
  color: #f39c12;
  font-size: 1.3rem;
}

.modal-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.btn-primary {
  background: linear-gradient(135deg, #e94560, #c0392b);
  color: #fff;
  border: none;
  padding: 0.75rem;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  transform: translateY(-2px);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #fff;
  padding: 0.6rem;
  border-radius: 8px;
  cursor: pointer;
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

.leaderboard {
  width: 100%;
  padding: 1rem;
  background: var(--bg-card);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  max-height: 132px;
  overflow: auto;
  flex-shrink: 0;
}

.leaderboard h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.leaderboard-grid {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.lb-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0.8rem;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 8px;
  border: 1px solid transparent;
}

.lb-row.me {
  border-color: var(--success);
  background: rgba(46, 204, 113, 0.1);
}

.lb-row.dead {
  opacity: 0.5;
}

.lb-player {
  font-weight: 600;
  font-size: 0.85rem;
  color: #fff;
}

.lb-hp {
  color: var(--success);
  font-weight: 600;
  font-size: 0.85rem;
}

.lb-score {
  color: var(--warning);
  font-weight: 600;
  font-size: 0.85rem;
}

.lb-status {
  font-size: 0.8rem;
  color: var(--text-muted);
}
</style>
