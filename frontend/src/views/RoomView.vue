<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameRoom } from '@/composables/useGameRoom'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, addBot, removeBot, leaveRoom, deleteRoom } from '@/api/rooms'
import { GAME_TYPE_INFO, GameType } from '@/types/game'
import type { GameRoom } from '@/types/game'

const route = useRoute()
const router = useRouter()
const session = useGameSession()
const gameRoom = useGameRoom()

const roomId = computed(() => route.params.roomId as string)
const room = ref<GameRoom | null>(null)
const ready = ref(false)
const addingBot = ref(false)
const starting = ref(false)

const destination = computed(() => {
  const type = room.value?.gameType
  if (type === GameType.SNAKE || type === GameType.TANK) return type.toLowerCase()
  return 'turn'
})

const hasBot = computed(() =>
  room.value?.players.some((p) => p.id.startsWith('bot-')) ?? false
)

const canAddBot = computed(() => {
  if (!room.value) return false
  return !GAME_TYPE_INFO[room.value.gameType].solo && room.value.players.length < room.value.maxPlayers
})

const isHost = computed(() => room.value?.hostId === session.player.id)

const allPlayersReady = computed(() =>
  (room.value?.players.length ?? 0) >= ((room.value && GAME_TYPE_INFO[room.value.gameType].solo) || room.value?.gameType === 'SNAKE' || room.value?.gameType === 'TANK' ? 1 : 2) &&
  (room.value?.players.every((p) => p.ready) ?? false)
)

const canStartGame = computed(() =>
  isHost.value &&
  allPlayersReady.value &&
  room.value?.status === 'WAITING'
)

async function handleAddBot() {
  if (!room.value || !canAddBot.value || addingBot.value) return
  addingBot.value = true
  try {
    const updated = await addBot(roomId.value)
    room.value = updated
    session.setCurrentRoom(updated)
  } catch (e) {
    console.error('Failed to add bot:', e)
  } finally {
    addingBot.value = false
  }
}

async function handleRemoveBot() {
  if (!room.value || !hasBot.value) return
  try {
    const updated = await removeBot(roomId.value)
    room.value = updated
    session.setCurrentRoom(updated)
  } catch (e) {
    console.error('Failed to remove bot:', e)
  }
}

watch(() => gameRoom.room.value, (newRoom) => {
  if (newRoom) {
    room.value = newRoom
    session.setCurrentRoom(newRoom)
    const me = newRoom.players.find(p => p.id === session.player.id)
    if (me) ready.value = me.ready
  }
})

onMounted(async () => {
  if (session.currentRoom && session.currentRoom.id === roomId.value) {
    room.value = session.currentRoom
  } else {
    try {
      room.value = await getRoom(roomId.value)
      session.setCurrentRoom(room.value)
    } catch (e) {
      console.error('Failed to fetch room:', e)
    }
  }

  // If room already in progress, navigate directly to game
  if (room.value?.status === 'IN_PROGRESS') {
    router.replace({ path: gamePath.value, query: route.query })
    return
  }

  // Host joins as ready, others join as not ready
  const joinAsReady = room.value?.hostId === session.player.id
  ready.value = joinAsReady

  await gameRoom.join(
    roomId.value,
    {
      id: session.player.id,
      username: session.player.username,
      sessionId: session.player.sessionId,
      score: 0,
      ready: joinAsReady,
      state: 'WAITING'
    },
    destination.value
  )

  gameRoom.subscribe(`/topic/room/${roomId.value}/state`, (state: GameRoom) => {
    room.value = state
    session.setCurrentRoom(state)
    const me = state.players.find(p => p.id === session.player.id)
    if (me) ready.value = me.ready
    if (state.status === 'IN_PROGRESS') {
      router.push({ path: gamePath.value, query: route.query })
    }
  })

  gameRoom.subscribe(`/topic/game/${roomId.value}/started`, () => {
    router.push({ path: gamePath.value, query: route.query })
  })

  // Redirect everyone if the room is deleted (e.g. host closes it)
  gameRoom.subscribe(`/topic/room/${roomId.value}/deleted`, () => {
    session.setCurrentRoom(null)
    router.replace('/')
  })
})

onUnmounted(() => {
  gameRoom.leave()
})

async function handleLeaveRoom() {
  try {
    // If host: delete the whole room (kicks all players)
    if (isHost.value && room.value?.status === 'WAITING') {
      await deleteRoom(roomId.value, session.player.id)
    } else {
      // Regular player: just leave
      await leaveRoom(roomId.value, session.player.id)
    }
  } catch (e) {
    console.error('Failed to leave room:', e)
  } finally {
    session.setCurrentRoom(null)
    router.push('/')
  }
}

function handleReady() {
  ready.value = !ready.value
  gameRoom.setReady(roomId.value, session.player.id, destination.value)
}

async function handleStartGame() {
  if (!canStartGame.value || starting.value) return
  starting.value = true
  // Adding a bot makes everyone ready but does not start the backend by itself.
  // Ask the server to start; room/state and game/started drive navigation.
  ready.value = true
  gameRoom.setReady(roomId.value, session.player.id, destination.value)
  setTimeout(() => {
    starting.value = false
  }, 2000)
}

const gamePath = computed(() => {
  const type = room.value?.gameType
  switch (type) {
    case GameType.SNAKE: return `/game/snake/${roomId.value}`
    case GameType.TANK: return `/game/tank/${roomId.value}`
    case GameType.CHESS: return `/game/chess/${roomId.value}`
    case GameType.CHINESE_CHESS: return `/game/chinese-chess/${roomId.value}`
    case GameType.CONNECT_FOUR: return `/game/connect-four/${roomId.value}`
    case GameType.CARO: return `/game/caro/${roomId.value}`
    case GameType.OTHELLO: return `/game/othello/${roomId.value}`
    case GameType.BATTLESHIP: return `/game/battleship/${roomId.value}`
    case GameType.MATCH_THREE: return `/game/match-three/${roomId.value}`
    case GameType.BOMBERMAN: return `/game/bomberman/${roomId.value}`
    case GameType.WARLORDS_CAMPAIGN: return `/game/warlords-campaign/${roomId.value}`
    case GameType.WARLORDS_DUEL: return `/game/warlords-duel/${roomId.value}`
    default: return '/'
  }
})
</script>

<template>
  <div class="room-waiting" v-if="room">
    <div class="card room-header">
      <div class="room-top">
        <div class="room-title">
          <span class="game-icon">{{ GAME_TYPE_INFO[room.gameType]?.icon }}</span>
          <div>
            <h2>{{ room.name }}</h2>
            <p class="text-muted">{{ GAME_TYPE_INFO[room.gameType]?.name }}</p>
          </div>
        </div>
        <span class="badge status-waiting">{{ room.status }}</span>
      </div>

      <div class="room-meta">
        <span>👥 {{ room.players.length }}/{{ room.maxPlayers }}</span>
        <span v-if="['CHESS','CHINESE_CHESS','CONNECT_FOUR','CARO','OTHELLO'].includes(room.gameType)">
          🧠 Bot: {{ room.settings?.customSettings?.difficulty === 'HARD' ? 'Khó' : room.settings?.customSettings?.difficulty === 'EASY' ? 'Dễ' : 'Vừa' }}
        </span>
      </div>
    </div>

    <div class="card players-list">
      <h3>Người chơi</h3>
      <div class="player-row" v-for="p in room.players" :key="p.id">
        <span class="player-name">
          {{ p.username }}
          <span v-if="p.id === session.player.id" class="you-badge">(bạn)</span>
          <span v-else-if="p.id.startsWith('bot-')" class="bot-badge">🤖 BOT</span>
        </span>
        <span class="badge" :class="p.ready ? 'ready' : 'not-ready'">
          {{ p.ready ? '✅ Sẵn sàng' : '⏳ Chờ' }}
        </span>
      </div>

      <div class="bot-controls" v-if="room.status === 'WAITING'">
        <button v-if="canAddBot" @click="handleAddBot" :disabled="addingBot" class="btn-bot add">
          🤖 + Thêm Bot
        </button>
        <button v-if="hasBot" @click="handleRemoveBot" class="btn-bot remove">
          🚫 - Bớt Bot
        </button>
      </div>

      <p v-if="room.players.length < 2 && !GAME_TYPE_INFO[room.gameType].solo" class="hint">
        Cần ít nhất 2 người chơi để bắt đầu (có thể thêm Bot để chơi cùng)
      </p>
    </div>

    <div class="room-actions">
      <button
        v-if="!ready"
        @click="handleReady"
        class="btn-ready"
      >
        ✅ Sẵn sàng
      </button>
      <button
        v-else-if="!canStartGame"
        @click="handleReady"
        class="btn-unready"
      >
        ⏳ Hủy sẵn sàng
      </button>

      <button
        v-if="canStartGame"
        @click="handleStartGame"
        :disabled="starting"
        class="btn-start"
      >
        {{ starting ? '⏳ Đang khởi động...' : '🚀 Bắt đầu Game!' }}
      </button>

      <button class="secondary" @click="handleLeaveRoom">{{ isHost ? '🗑️ Xóa phòng' : '🚪 Rời phòng' }}</button>
    </div>

    <div v-if="gameRoom.error" class="error-msg">{{ gameRoom.error }}</div>
  </div>

  <div v-else class="loading">Đang kết nối...</div>
</template>

<style scoped>
.room-waiting {
  width: 100%;
  max-width: 520px;
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 2rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border);
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.room-header {
  margin-bottom: 1.5rem;
}

.room-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.room-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.room-title h2 {
  margin: 0;
  font-size: 1.25rem;
  color: var(--text-primary);
}

.room-title .game-icon {
  font-size: 1.5rem;
}

.badge.status-waiting {
  background: rgba(155, 155, 155, 0.2);
  color: var(--text-secondary);
  padding: 0.4rem 0.8rem;
  border-radius: 20px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.room-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 1.5rem;
  border: 1px solid var(--border);
}

.players-list h3 {
  margin: 0 0 1rem 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.player-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--border-light);
  transition: background 0.2s;
}

.player-row:last-child {
  border-bottom: none;
}

.player-row:hover {
  background: var(--bg-hover);
}

.player-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.player-name .you-badge {
  color: var(--accent);
  font-size: 0.75rem;
  background: rgba(52, 152, 219, 0.2);
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  font-weight: 600;
}

.player-name .bot-badge {
  color: #9b59b6;
  font-size: 0.75rem;
  background: rgba(155, 89, 182, 0.2);
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  font-weight: 600;
}

.bot-controls {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.btn-bot {
  flex: 1;
  padding: 0.45rem 0.8rem;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px dashed var(--border);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.btn-bot.add:hover {
  border-color: #9b59b6;
  color: #9b59b6;
  background: rgba(155, 89, 182, 0.15);
}

.btn-bot.remove:hover {
  border-color: var(--danger);
  color: var(--danger);
  background: rgba(231, 76, 60, 0.15);
}

.btn-bot:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.badge.ready {
  background: rgba(46, 204, 113, 0.2);
  color: var(--success);
  padding: 0.4rem 0.6rem;
  border-radius: 18px;
  font-size: 0.7rem;
  font-weight: 600;
  min-width: 60px;
  text-align: center;
}

.badge.not-ready {
  background: rgba(243, 156, 18, 0.2);
  color: var(--warning);
  padding: 0.4rem 0.6rem;
  border-radius: 18px;
  font-size: 0.7rem;
  font-weight: 600;
  min-width: 60px;
  text-align: center;
}

.hint {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-top: 0.75rem;
  font-style: italic;
}

.room-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
}

.room-actions button {
  flex: 1;
  padding: 0.7rem 1rem;
  border: none;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 120px;
}

.btn-ready {
  background: rgba(46, 204, 113, 0.15);
  color: var(--success);
  border: 2px solid rgba(46, 204, 113, 0.4) !important;
}

.btn-ready:hover {
  background: rgba(46, 204, 113, 0.3);
  transform: translateY(-1px);
}

.btn-unready {
  background: rgba(243, 156, 18, 0.15);
  color: var(--warning);
  border: 2px solid rgba(243, 156, 18, 0.3) !important;
}

.btn-start {
  background: linear-gradient(135deg, #e94560, #c0392b);
  color: white;
  border: none !important;
  box-shadow: 0 4px 16px rgba(233, 69, 96, 0.4);
  animation: pulse-glow 1.5s ease-in-out infinite;
}

.btn-start:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(233, 69, 96, 0.5);
}

.btn-start:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  animation: none;
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 4px 16px rgba(233, 69, 96, 0.4); }
  50% { box-shadow: 0 4px 24px rgba(233, 69, 96, 0.7); }
}

.room-actions .btn-secondary {
  background: transparent;
  color: var(--text-primary);
  border: 2px solid var(--border);
}

.room-actions .btn-secondary:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.room-actions .btn-primary {
  background: var(--accent);
  color: white;
}

.room-actions .btn-primary:hover {
  background: #3498db;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(52, 152, 219, 0.3);
}

.room-actions .btn-primary:active {
  transform: translateY(0);
}

.error-msg {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
  border-radius: 10px;
  color: var(--danger);
  font-size: 0.85rem;
}

.loading {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}
</style>
