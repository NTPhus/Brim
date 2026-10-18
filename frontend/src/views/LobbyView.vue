<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getAllRooms, createRoom, joinRoom, getLeaderboard } from '@/api/rooms'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { GAME_TYPE_INFO } from '@/types/game'
import type { GameRoom, GameTypeCode, CreateRoomRequest, LeaderboardEntry } from '@/types/game'

const router = useRouter()
const session = useGameSession()
const stomp = useStompClient()

const rooms = ref<GameRoom[]>([])
const loading = ref(true)
const showCreateModal = ref(false)
const creatingRoom = ref(false)
const roomNameError = ref('')
const createRoomError = ref('')
const filterType = ref<GameTypeCode | 'ALL'>('ALL')
const leaderboard = ref<LeaderboardEntry[]>([])
const difficultyLevels = ['EASY', 'MEDIUM', 'HARD'] as const
let refreshTimer: number | undefined
let unsubscribeLeaderboard: (() => void) | undefined

const newRoom = ref<CreateRoomRequest>({
  name: '',
  gameType: 'SNAKE',
  maxPlayers: 2,
  difficulty: 'MEDIUM',
  mapId: 'ARENA'
})

const isBoardGame = computed(() => !GAME_TYPE_INFO[newRoom.value.gameType].realTime)
const hasBotDifficulty = computed(() => ['CHESS','CHINESE_CHESS','CONNECT_FOUR','CARO','OTHELLO'].includes(newRoom.value.gameType))

watch(showCreateModal, open => {
  if (open) { roomNameError.value = ''; createRoomError.value = '' }
})

watch(() => newRoom.value.gameType, (type) => {
  newRoom.value.maxPlayers = GAME_TYPE_INFO[type].solo ? 1 : !GAME_TYPE_INFO[type].realTime ? 2 : type === 'BOMBERMAN' ? 4 : (type === 'SNAKE' ? 8 : 6)
})

const filteredRooms = computed(() => {
  // Filter out rooms that are no longer joinable
  const joinable = rooms.value.filter(
    (r) => r.status !== 'FINISHED' && r.status !== 'CANCELLED'
  )
  if (filterType.value === 'ALL') return joinable
  return joinable.filter((r) => r.gameType === filterType.value)
})

onMounted(async () => {
  await Promise.all([refreshRooms(), refreshLeaderboard()])
  refreshTimer = window.setInterval(refreshRooms, 3000)
  try {
    await stomp.connect()
    unsubscribeLeaderboard = stomp.subscribe('/topic/leaderboard', (entries: LeaderboardEntry[]) => {
      leaderboard.value = entries
    })
  } catch (e) {
    console.warn('Leaderboard realtime unavailable:', e)
  }
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  unsubscribeLeaderboard?.()
})

async function refreshLeaderboard() {
  try { leaderboard.value = await getLeaderboard() } catch (e) { console.warn('Leaderboard unavailable:', e) }
}

async function refreshRooms() {
  try {
    rooms.value = await getAllRooms()
  } catch (e) {
    console.error('Failed to fetch rooms:', e)
  } finally {
    loading.value = false
  }
}

async function handleCreateRoom() {
  if (creatingRoom.value) return
  createRoomError.value = ''
  const name = newRoom.value.name.trim()
  if (!name || name.length > 80) {
    roomNameError.value = !name ? 'Vui lòng nhập tên phòng.' : 'Tên phòng tối đa 80 ký tự.'
    return
  }
  roomNameError.value = ''
  creatingRoom.value = true
  try {
    const room = await createRoom({
      ...newRoom.value,
      name,
      hostId: session.player.id,
      hostUsername: session.player.username
    })
    session.setCurrentRoom(room)
    showCreateModal.value = false
    await router.push(`/room/${room.id}`)
  } catch (e) {
    createRoomError.value = e instanceof TypeError
      ? 'Không thể kết nối tới máy chủ. Vui lòng thử lại.'
      : e instanceof Error ? e.message : 'Không thể tạo phòng. Vui lòng thử lại.'
  } finally {
    creatingRoom.value = false
  }
}

async function handleJoinRoom(roomId: string) {
  try {
    const room = await joinRoom(roomId, {
      username: session.player.username,
      sessionId: session.player.sessionId,
      playerId: session.player.id
    })
    session.setCurrentRoom(room)
    await router.push(`/room/${room.id}`)
  } catch (e) {
    console.error('Failed to join room:', e)
  }
}

function statusClass(status: string) {
  return `status-${status.toLowerCase()}`
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    WAITING: 'Chờ người chơi',
    IN_PROGRESS: 'Đang chơi',
    FINISHED: 'Kết thúc',
    CANCELLED: 'Đã hủy'
  }
  return map[status] || status
}
</script>

<template>
  <div class="lobby">
    <section class="hero-panel">
      <div>
        <span class="eyebrow">BRIM ARCADE • MULTIPLAYER</span>
        <h2>Chọn đấu trường.<br><span>Chinh phục bảng xếp hạng.</span></h2>
        <p>Cờ chiến thuật, rắn sinh tồn và tank battle — tất cả cập nhật realtime.</p>
      </div>
      <button class="hero-cta" @click="showCreateModal = true">Tạo trận mới <span>→</span></button>
    </section>

    <section class="warlords-entry">
      <div><span class="eyebrow">GAME MỚI · MATCH-3 CHIẾN ĐẤU</span><h2>⚔ Sơn Hà — Hợp cờ Hoa Lư</h2><p>Ghép kiếm hạ địch, tích năng lượng dùng kỹ năng. Đi 12 chương chiến dịch hoặc tạo bàn đấu tướng cùng bạn bè.</p></div>
      <button @click="router.push('/warlords')">Vào Sơn Hà →</button>
    </section>

    <div class="lobby-header">
      <div class="player-info card">
        <label>Username:</label>
        <input
          :value="session.player.username"
          @change="session.setUsername(($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="lobby-actions">
        <select v-model="filterType">
          <option value="ALL">Tất cả game</option>
          <option v-for="(info, type) in GAME_TYPE_INFO" :key="type" :value="type">{{ info.icon }} {{ info.name }}</option>
        </select>
        <button @click="showCreateModal = true">+ Tạo phòng</button>
        <button class="secondary" @click="refreshRooms">🔄 Làm mới</button>
      </div>
    </div>

    <section class="leaderboard-panel">
      <div class="section-heading">
        <div><span class="eyebrow">LIVE • HÔM NAY</span><h2>🏆 Bảng xếp hạng ngày</h2></div>
        <span class="live-dot">● LIVE</span>
      </div>
      <div class="ranking-grid">
        <div v-for="(entry, index) in leaderboard.slice(0, 6)" :key="`${entry.gameType}-${entry.playerId}`" class="rank-row">
          <span class="rank" :class="`top-${index + 1}`">{{ index + 1 }}</span>
          <span class="rank-icon">{{ GAME_TYPE_INFO[entry.gameType].icon }}</span>
          <span class="rank-name">{{ entry.username }}</span>
          <strong>{{ entry.score.toLocaleString() }} pts</strong>
        </div>
      </div>
      <p v-if="!leaderboard.length" class="ranking-empty">Chơi một trận để trở thành người đầu tiên trên bảng xếp hạng.</p>
    </section>

    <p v-if="loading" class="loading">Đang tải...</p>

    <div v-else-if="filteredRooms.length === 0" class="empty-state">
      <p>Chưa có phòng nào. Tạo phòng mới để bắt đầu!</p>
    </div>

    <div v-else class="room-grid">
      <div
        v-for="room in filteredRooms"
        :key="room.id"
        class="room-card card"
        @click="handleJoinRoom(room.id)"
      >
        <div class="room-card-header">
          <span class="game-icon">{{ GAME_TYPE_INFO[room.gameType]?.icon ?? '🎲' }}</span>
          <h3>{{ room.name }}</h3>
        </div>
        <div class="room-card-body">
          <span class="badge" :class="statusClass(room.status)">
            {{ statusLabel(room.status) }}
          </span>
          <span class="players">
            👥 {{ room.players.length }}/{{ room.maxPlayers }}
          </span>
        </div>
        <div class="room-card-footer">
          <span class="game-type">{{ GAME_TYPE_INFO[room.gameType]?.name ?? room.gameType }}</span>
          <span v-if="['CHESS','CHINESE_CHESS','CONNECT_FOUR','CARO','OTHELLO'].includes(room.gameType)" class="difficulty-badge">
            🧠 {{ room.settings?.customSettings?.difficulty === 'HARD' ? 'Khó' : room.settings?.customSettings?.difficulty === 'EASY' ? 'Dễ' : 'Vừa' }}
          </span>
          <span v-if="GAME_TYPE_INFO[room.gameType]?.realTime" class="realtime-badge">⚡ Real-time</span>
        </div>
      </div>
    </div>

    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <form class="modal card" novalidate :aria-busy="creatingRoom" @submit.prevent="handleCreateRoom">
        <h2>Tạo phòng mới</h2>

        <label for="room-name">Tên phòng (bắt buộc)</label>
        <input id="room-name" v-model="newRoom.name" name="name" required maxlength="80"
          :aria-invalid="!!roomNameError" :aria-describedby="roomNameError ? 'room-name-error' : 'room-name-help'"
          placeholder="VD: Phòng rắn chiều thứ 7" @input="roomNameError = ''; createRoomError = ''" />
        <p v-if="roomNameError" id="room-name-error" class="form-error" role="alert">{{ roomNameError }}</p>
        <p v-else id="room-name-help" class="form-help">Nhập tên phòng từ 1 đến 80 ký tự.</p>

        <label>Loại game</label>
        <select v-model="newRoom.gameType">
          <option v-for="(info, type) in GAME_TYPE_INFO" :key="type" :value="type">{{ info.icon }} {{ info.name }}</option>
        </select>

        <label>Số người chơi tối đa</label>
        <input v-model.number="newRoom.maxPlayers" type="number" :min="GAME_TYPE_INFO[newRoom.gameType].solo ? 1 : 2" :max="newRoom.gameType === 'BOMBERMAN' ? 4 : newRoom.gameType === 'SNAKE' ? 12 : 8" :disabled="isBoardGame" />

        <template v-if="newRoom.gameType === 'TANK'">
          <label>Bản đồ Tank</label>
          <select v-model="newRoom.mapId">
            <option value="ARENA">Arena · Đấu trường mở</option>
            <option value="WAREHOUSE">Warehouse · Nhà kho</option>
            <option value="CROSSROADS">Crossroads · Giao lộ</option>
          </select>
        </template>

        <template v-if="hasBotDifficulty">
          <label>Độ khó Bot</label>
          <div class="difficulty-options">
            <button v-for="level in difficultyLevels" :key="level" type="button"
              :class="{ active: newRoom.difficulty === level }" @click="newRoom.difficulty = level">
              {{ level === 'EASY' ? 'Dễ' : level === 'MEDIUM' ? 'Vừa' : 'Khó' }}
            </button>
          </div>
        </template>

        <p v-if="createRoomError" class="form-error" role="alert">{{ createRoomError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="showCreateModal = false">Hủy</button>
          <button type="submit" :disabled="creatingRoom">{{ creatingRoom ? 'Đang tạo phòng…' : 'Tạo & Vào phòng' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.warlords-entry { display:flex;align-items:center;justify-content:space-between;gap:1.5rem;padding:1.5rem;margin-bottom:1.5rem;border:1px solid #bf9a5966;border-radius:18px;background:radial-gradient(ellipse at 0 0,#63502f66,transparent 80%),#1b2325;color:#f0dfba; }
.warlords-entry .eyebrow { color:#d9b975; }.warlords-entry h2 { font-family:Georgia,serif;margin:.6rem 0;font-size:1.35rem; }.warlords-entry p { color:#c1b297;font-size:.85rem;line-height:1.7;max-width:650px; }.warlords-entry button { flex-shrink:0;background:#d7b66e;color:#251c10;font-weight:800; }
@media(max-width:650px) { .warlords-entry { flex-direction:column;align-items:stretch; } }
.form-error { color: #fda4af; font-size: .85rem; margin: .4rem 0; }
.form-help { color: var(--text-secondary); font-size: .8rem; margin: .4rem 0; }
input[aria-invalid="true"] { border-color: #fda4af; }
.lobby {
  width: 100%;
  max-width: 1200px;
  padding: 2rem;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.hero-panel {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 2rem;
  padding: 2.5rem;
  margin-bottom: 1.5rem;
  border: 1px solid rgba(111, 224, 255, 0.2);
  border-radius: 24px;
  background: radial-gradient(circle at 85% 20%, rgba(52, 152, 219, .28), transparent 35%), linear-gradient(135deg, #181c3a, #10162b);
  box-shadow: 0 20px 60px rgba(0, 0, 0, .28);
}

.hero-panel::after { content: ''; position: absolute; width: 240px; height: 240px; right: -70px; top: -90px; border: 1px solid rgba(255,255,255,.09); border-radius: 50%; }
.eyebrow { color: #67e8f9; font-size: .72rem; font-weight: 800; letter-spacing: .16em; }
.hero-panel h2 { margin: .5rem 0; font-size: clamp(2rem, 4vw, 3.4rem); line-height: 1.05; letter-spacing: -.04em; }
.hero-panel h2 span { color: #fb7185; }
.hero-panel p { color: var(--text-secondary); max-width: 620px; }
.hero-cta { z-index: 1; white-space: nowrap; padding: .9rem 1.25rem; border-radius: 12px; background: linear-gradient(135deg, #fb7185, #e94560); }

.leaderboard-panel { margin: 0 0 2rem; padding: 1.25rem; border-radius: 18px; background: rgba(8, 12, 28, .72); border: 1px solid rgba(255,255,255,.08); }
.section-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.section-heading h2 { margin: .2rem 0 0; font-size: 1.2rem; }
.live-dot { color: #4ade80; font-size: .72rem; font-weight: 800; letter-spacing: .1em; }
.ranking-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: .6rem; }
.rank-row { display: grid; grid-template-columns: 28px 30px 1fr auto; align-items: center; gap: .5rem; padding: .7rem .8rem; border-radius: 11px; background: rgba(255,255,255,.045); }
.rank { color: var(--text-muted); font-weight: 800; text-align: center; }
.rank.top-1 { color: #facc15; } .rank.top-2 { color: #cbd5e1; } .rank.top-3 { color: #fb923c; }
.rank-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-row strong { color: #67e8f9; font-size: .85rem; }
.ranking-empty { padding: 1rem; color: var(--text-muted); text-align: center; border: 1px dashed var(--border); border-radius: 12px; }
.difficulty-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: .5rem; }
.difficulty-options button { background: rgba(255,255,255,.06); border: 1px solid var(--border); }
.difficulty-options button.active { background: rgba(233,69,96,.2); color: #fb7185; border-color: #fb7185; }

@media (max-width: 700px) {
  .hero-panel { align-items: stretch; flex-direction: column; padding: 1.5rem; }
  .ranking-grid { grid-template-columns: 1fr; }
}

.lobby-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.player-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.player-info input {
  width: 200px;
  padding: 0.5rem 1rem;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 1rem;
  background: var(--bg-input);
  color: var(--text-primary);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.player-info input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.15);
}

.lobby-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.select-wrapper {
  position: relative;
  display: inline-block;
}

.select-wrapper::after {
  content: '▼';
  position: absolute;
  right: 1rem;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
  color: var(--text-muted);
  font-size: 0.7rem;
}

.select-wrapper select {
  appearance: none;
  padding-right: 2.5rem;
}

.lobby .btn {
  padding: 0.6rem 1.2rem;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  background: transparent;
  color: var(--text-primary);
}

.lobby .btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.lobby .btn.secondary:hover {
  border-color: var(--warning);
  color: var(--warning);
}

.lobby .btn:active {
  transform: translateY(0);
}

.loading,
.empty-state {
  text-align: center;
  color: var(--text-secondary);
  padding: 4rem 2rem;
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
  margin-top: 1rem;
}

.room-card {
  cursor: pointer;
  border-radius: 16px;
  overflow: hidden;
  transition: transform 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
  background: linear-gradient(145deg, rgba(24, 34, 62, .92), rgba(14, 20, 39, .92));
  border: 1px solid var(--border);
}

.difficulty-badge { color: #c4b5fd; font-size: .76rem; font-weight: 700; }

.room-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
  border-color: var(--accent);
}

.room-card:active {
  transform: translateY(0);
}

.room-card:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.room-card-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1rem 0.5rem;
}

.game-icon {
  font-size: 1.75rem;
}

.room-card-header h3 {
  font-size: 1.15rem;
  margin: 0;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.room-card-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem 0.75rem;
  margin-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-light);
}

.room-card-body:last-child {
  border-bottom: none;
}

.badge {
  padding: 0.4rem 0.8rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.badge.status-waiting {
  background: rgba(155, 155, 155, 0.2);
  color: var(--text-secondary);
}

.badge.status-running {
  background: rgba(46, 204, 113, 0.2);
  color: var(--success);
}

.badge.status-finished {
  background: rgba(149, 165, 166, 0.2);
  color: var(--text-muted);
}

.badge.status-cancelled {
  background: rgba(231, 76, 60, 0.2);
  color: var(--danger);
}

.players {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.game-type {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.realtime-badge {
  color: var(--warning);
  font-size: 0.7rem;
  background: rgba(233, 196, 105, 0.2);
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  margin-left: 0.5rem;
}

.room-card-footer {
  display: flex;
  align-items: center;
  gap: .55rem;
  padding: .25rem 1rem 1rem;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  width: 440px;
  max-width: 95vw;
  background: var(--bg-card);
  border-radius: 20px;
  padding: 2rem;
  border: 1px solid var(--border);
  animation: modalIn 0.3s ease-out;
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal h2 {
  margin: 0 0 1.5rem 0;
  font-size: 1.5rem;
  color: var(--text-primary);
  text-align: center;
}

.modal label {
  display: block;
  margin: 0.8rem 0 0.5rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.modal input,
.modal select {
  width: 100%;
  padding: 0.6rem 1rem;
  margin-bottom: 1rem;
  border: 2px solid var(--border);
  border-radius: 10px;
  font-size: 1rem;
  background: var(--bg-input);
  color: var(--text-primary);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.modal input:focus,
.modal select:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.15);
}

.modal select option {
  background: var(--bg-primary);
  color: var(--text-primary);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-light);
}

.modal-actions .btn {
  flex: 1;
}

/* Reduced motion for accessibility */
@media (prefers-reduced-motion: reduce) {
  .room-card:hover {
    transform: none;
  }
  .modal-overlay {
    animation: none;
  }
}
</style>
