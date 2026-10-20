<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import Phaser from 'phaser'
import { useRoute, useRouter } from 'vue-router'
import { tacticalMessages } from '@/types/tactical'
import { tankMap, tankOperation } from '@/game/tank/maps'
import { TankScene } from '@/game/tank/TankScene'
import GameMinimap from '@/components/GameMinimap.vue'
import GlobalLeaderboard from '@/components/GlobalLeaderboard.vue'
import { useStompClient } from '@/composables/useStompClient'
import { useGameSession } from '@/stores/gameSession'
import { getRoom, leaveRoom } from '@/api/rooms'
import { MessageType } from '@/types/game'
import type { GameMessage, GameRoom } from '@/types/game'
import type { TankGameState, TankWall } from '@/types/gameState'

const route = useRoute(), router = useRouter(), session = useGameSession(), stomp = useStompClient()
const roomId = route.params.roomId as string, myPlayerId = session.player.id
const gameContainer = ref<HTMLDivElement | null>(null), arena = ref<HTMLDivElement | null>(null)
const tankShell = ref<HTMLDivElement | null>(null), battleColumn = ref<HTMLElement | null>(null)
const gameState = ref<TankGameState | null>(null), roomInfo = ref<GameRoom | null>(null)
const selectedColor = ref(localStorage.getItem('brim_tank_color') || '#2ecc71')
const clock = ref(Date.now()), lastSnapshotAt = ref(0), isConnecting = ref(true), isGameOver = ref(false), isRestarting = ref(false)
const gameOverMessage = ref(''), connectionError = ref(''), feedback = ref(''), feedbackAt = ref(0)
const helpOpen = ref(false), upgradesOpen = ref(true)
const rankOpen = ref(false)
const pendingCommand = ref<{ commandSeq: number; action: string } | null>(null)
const pendingUpgrade = ref<{ branch: string; level: number; sentAt: number } | null>(null)
const input = ref({ forward: false, backward: false, turnLeft: false, turnRight: false, shoot: false })
const mouseShooting = ref(false)
const awaitingFreshSnapshot = ref(false)
const pressedKeys = new Set<string>(), touchKeys = new Set<keyof typeof input.value>(), cleanupFns: Array<() => void> = []
let game: Phaser.Game | null = null, tankScene: TankScene | null = null
let clockTimer: number | undefined, readyTimer: number | undefined
let restartRequestedAt = 0, commandSeq = 0, disposed = false
let waitingForRoundStart = false, awaitingRoundSnapshot = false, roundStartedAt = 0

const assault = computed(() => (gameState.value?.mode ?? roomInfo.value?.settings.customSettings?.tankMode) === 'ASSAULT')
const myTank = computed(() => gameState.value?.tanks[myPlayerId])
const tactical = computed(() => myTank.value?.tacticalView)
const health = computed(() => myTank.value?.health ?? 0), score = computed(() => myTank.value?.score ?? 0)
const maxHealth = computed(() => myTank.value?.maxHealth || (assault.value ? 1200 : 100))
const healthPercent = computed(() => Math.max(0, Math.min(100, health.value / maxHealth.value * 100)))
const alive = computed(() => myTank.value?.alive ?? true)
const isHost = computed(() => roomInfo.value?.hostId === myPlayerId)
const mapName = computed(() => tankMap(gameState.value?.mapId ?? String(roomInfo.value?.settings.customSettings?.mapId ?? 'ARENA')).name)
const operation = computed(() => tankOperation(gameState.value?.mapId ?? String(roomInfo.value?.settings.customSettings?.mapId ?? 'ARENA')))
const mission = computed(() => gameState.value?.mission)
const objectiveName = computed(() => operation.value.sectors[(mission.value?.sector ?? 1) - 1] ?? 'Khu vực nhiệm vụ')
const objectiveProgress = computed(() => mission.value ? Math.min(100, mission.value.heldMs / mission.value.requiredMs * 100) : 0)
const objectiveDistance = computed(() => mission.value && myTank.value ? Math.round(Math.hypot(myTank.value.x-mission.value.x,myTank.value.y-mission.value.y)) : null)
const objectiveStatus = computed(() => {
  const current = mission.value
  if (!current) return 'Trinh sát và giữ vật tư'
  if (current.nextWaveMs >= 0) return `Viện binh sau ${(current.nextWaveMs / 1000).toFixed(1)}s`
  if (current.secured) return 'Đã giữ điểm · đẩy lùi lực lượng còn lại'
  if (current.status === 'CONTESTED') return 'Đang tranh chấp · đẩy địch khỏi vòng cờ'
  if (current.status === 'HOLDING') return `Đang giữ điểm · ${(current.heldMs / 1000).toFixed(1)} / ${current.requiredMs / 1000}s`
  return 'Tiến vào vòng cờ và giữ 8 giây'
})
const nearbyWarning = computed(() => !!myTank.value && (gameState.value?.warnings ?? []).some(warning => Math.hypot(warning.x-myTank.value!.x,warning.y-myTank.value!.y) <= warning.radius + 25))
const networkState = computed(() => !stomp.connected.value ? 'offline' : lastSnapshotAt.value && clock.value - lastSnapshotAt.value > 1500 ? 'slow' : 'online')
const networkStatus = computed(() => isConnecting.value ? 'Đang kết nối' : networkState.value === 'offline' ? 'Đang kết nối lại' : networkState.value === 'slow' ? 'Đang chờ cập nhật' : 'Kết nối ổn định')
const canControl = computed(() => stomp.connected.value && !awaitingFreshSnapshot.value && !!myTank.value && alive.value && gameState.value?.state === 'IN_PROGRESS' && !isGameOver.value && !isRestarting.value)
const fuelPercent = computed(() => tactical.value?.fuelCapacity ? Math.max(0, Math.min(100, tactical.value.fuel / tactical.value.fuelCapacity * 100)) : 0)
const repairing = computed(() => (tactical.value?.repairRemainingMs ?? 0) > 0)
const repairCeiling = computed(() => Math.floor(maxHealth.value * .7))
const reloadProgress = computed(() => tactical.value?.weapon.state === 'READY' ? 100 : tactical.value?.weapon.state === 'LOADING' ? Math.max(0, Math.min(100, (6000 - tactical.value.weapon.remainingMs) / 60)) : 0)
const weaponLabel = computed(() => ({ READY: 'Sẵn sàng khai hỏa', LOADING: 'Đang nạp pháo', EMPTY: 'Đã hết đạn', DISABLED: 'Pháo ngừng hoạt động' })[tactical.value?.weapon.state ?? 'DISABLED'])
const visibleContacts = computed(() => gameState.value?.contacts?.filter(contact => contact.current).length ?? 0)
const oldContacts = computed(() => gameState.value?.contacts?.filter(contact => !contact.current).length ?? 0)
const respawnRemaining = computed(() => Math.max(0, Math.ceil(((myTank.value?.diedAt ?? 0) + 3000 - clock.value) / 1000)))
const activeEffects = computed(() => [
  { label: 'Khiên', until: myTank.value?.shieldUntil ?? 0 }, { label: 'Tăng tốc', until: myTank.value?.speedUntil ?? 0 },
  { label: 'Bắn nhanh', until: myTank.value?.rapidFireUntil ?? 0 }
].filter(effect => effect.until > clock.value))
const team = computed(() => Object.values(gameState.value?.tanks ?? {}).filter(tank => !assault.value || !tank.playerId.startsWith('bot-')).sort((a, b) => Number(b.playerId === myPlayerId) - Number(a.playerId === myPlayerId) || b.score - a.score))

// The server requires both proximity and a clear path to the nearest crate.
function wallBetween(wall: TankWall, x: number, y: number, tx: number, ty: number) {
  if ((wall as TankWall & { blocksMovement?: boolean }).blocksMovement === false) return false
  let entry = 0, exit = 1
  for (const [origin, delta, min, max] of [[x, tx - x, wall.x, wall.x + wall.width], [y, ty - y, wall.y, wall.y + wall.height]]) {
    if (Math.abs(delta) < 1e-8) { if (origin < min || origin > max) return false }
    else {
      const first = (min - origin) / delta, second = (max - origin) / delta
      entry = Math.max(entry, Math.min(first, second)); exit = Math.min(exit, Math.max(first, second))
      if (entry > exit) return false
    }
  }
  return true
}
const nearbySupply = computed(() => {
  const tank = myTank.value
  if (!tank) return null
  return (gameState.value?.supplies ?? []).filter(crate => crate.quantity > 0 && Math.hypot(crate.x - tank.x, crate.y - tank.y) <= 65
    && !(gameState.value?.walls ?? []).some(wall => wallBetween(wall, tank.x, tank.y, crate.x, crate.y)))
    .sort((a, b) => Math.hypot(a.x - tank.x, a.y - tank.y) - Math.hypot(b.x - tank.x, b.y - tank.y))[0] ?? null
})
const supplyLabel = computed(() => nearbySupply.value ? ({ SHELLS: 'Hòm đạn pháo', FUEL: 'Hòm nhiên liệu', REPAIR: 'Hòm bộ sửa' })[nearbySupply.value.kind] : 'Tiến gần hòm vật tư để nhận')
const inventoryFull = computed(() => {
  const crate = nearbySupply.value, vehicle = tactical.value
  if (!crate || !vehicle) return false
  if (crate.kind === 'SHELLS') return vehicle.weapon.chamber + vehicle.weapon.reserve >= 12
  if (crate.kind === 'FUEL') return vehicle.fuel >= vehicle.fuelCapacity - .01
  return vehicle.repairKits + Number(repairing.value) >= 2
})
const repairReason = computed(() => !alive.value ? 'Xe đã bị phá hủy' : repairing.value ? 'Giữ yên để hoàn tất sửa chữa' : !tactical.value?.repairKits ? 'Cần một bộ sửa chữa' : health.value >= repairCeiling.value ? 'Chỉ sửa khi HP dưới 70%' : Math.abs(tactical.value?.speed ?? 0) >= .1 || Object.values(input.value).some(Boolean) || mouseShooting.value ? 'Dừng xe và ngừng bắn trước khi sửa' : `Hồi tối đa 180 HP, tới ${repairCeiling.value} HP`)
const canRepair = computed(() => canControl.value && !!tactical.value && !repairing.value && tactical.value.repairKits > 0 && health.value < repairCeiling.value && Math.abs(tactical.value.speed) < .1 && !Object.values(input.value).some(Boolean) && !mouseShooting.value && !pendingCommand.value)
const canSupply = computed(() => canControl.value && !!nearbySupply.value && !inventoryFull.value && !pendingCommand.value)
const commandNotice = computed(() => pendingCommand.value ? 'Đang chờ xác nhận thao tác…' : clock.value - feedbackAt.value < 6500 ? feedback.value : '')
const upgrades = computed(() => [
  { branch: 'ENGINE', symbol: '↗', label: 'Động cơ', level: myTank.value?.engineLevel ?? 0, detail: '+10% quãng đường / cấp' },
  { branch: 'CANNON', symbol: '⌖', label: 'Pháo', level: myTank.value?.cannonLevel ?? 0, detail: '+15 sát thương / cấp · nạp 6s' },
  { branch: 'ARMOR', symbol: '◇', label: 'Giáp', level: myTank.value?.armorLevel ?? 0, detail: '+35 HP / cấp' }
].map(item => ({ ...item, cost: (item.level + 1) * 100, affordable: (myTank.value?.credits ?? 0) >= (item.level + 1) * 100 })))

function notify(message: string) { feedback.value = message; feedbackAt.value = Date.now() }
function issueCommand(action: 'INTERACT' | 'REPAIR') {
  if (!assault.value || !canControl.value || pendingCommand.value) return
  if (action === 'REPAIR' && tankScene?.getAim()?.shooting) { notify('Dừng xe và ngừng bắn trước khi sửa.'); return }
  if (action === 'REPAIR' && !canRepair.value) { notify(repairReason.value); return }
  if (action === 'INTERACT' && !canSupply.value) { notify(inventoryFull.value ? 'Đã mang đủ vật tư loại này.' : 'Cần tới gần hòm tiếp tế và có đường tiếp cận.'); return }
  commandSeq = Math.max(commandSeq, tactical.value?.lastCommandSeq ?? 0) + 1
  pendingCommand.value = { commandSeq, action }; sendInput()
}
function upgrade(branch: string) {
  const item = upgrades.value.find(upgrade => upgrade.branch === branch)
  if (!canControl.value || !item || item.level >= 3 || !item.affordable || pendingUpgrade.value) return
  pendingUpgrade.value = { branch, level: item.level, sentAt: Date.now() }
  stomp.publish(`/app/tank/${roomId}/upgrade`, { type: MessageType.ACTION, roomId, playerId: myPlayerId, payload: { branch } })
}
function sendInput() {
  if (!stomp.connected.value) return
  const aim = tankScene?.getAim()
  stomp.publish(`/app/tank/${roomId}/input`, { type: MessageType.ACTION, roomId, playerId: myPlayerId,
    payload: { movingForward: canControl.value && input.value.forward, movingBackward: canControl.value && input.value.backward,
      turningLeft: canControl.value && input.value.turnLeft, turningRight: canControl.value && input.value.turnRight,
      shooting: canControl.value && (input.value.shoot || !!aim?.shooting), ...(pendingCommand.value ?? {}),
      ...(aim ? { targetX: aim.targetX, targetY: aim.targetY } : {}) } })
}
function updateInputState() {
  input.value = { forward: pressedKeys.has('KeyW') || pressedKeys.has('ArrowUp') || touchKeys.has('forward'),
    backward: pressedKeys.has('KeyS') || pressedKeys.has('ArrowDown') || touchKeys.has('backward'),
    turnLeft: pressedKeys.has('KeyA') || pressedKeys.has('ArrowLeft') || touchKeys.has('turnLeft'),
    turnRight: pressedKeys.has('KeyD') || pressedKeys.has('ArrowRight') || touchKeys.has('turnRight'),
    shoot: pressedKeys.has('Space') || touchKeys.has('shoot') }; sendInput()
}
function releaseInput() { pressedKeys.clear(); touchKeys.clear(); mouseShooting.value = false; tankScene?.releasePointer(); updateInputState() }
function holdControl(event: PointerEvent, key: keyof typeof input.value) {
  if (!canControl.value) return
  event.preventDefault(); (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  touchKeys.add(key); updateInputState()
}
function releaseControl(key: keyof typeof input.value) { touchKeys.delete(key); updateInputState() }
function handleRestart() {
  if (!isHost.value || !stomp.connected.value || isRestarting.value) return
  releaseInput(); pendingCommand.value = null; pendingUpgrade.value = null
  isRestarting.value = true; waitingForRoundStart = true; restartRequestedAt = Date.now()
  stomp.publish(`/app/tank/${roomId}/restart`, { type: MessageType.ACTION, roomId, playerId: myPlayerId, payload: null })
}
function handleRespawn() {
  if (assault.value || respawnRemaining.value > 0 || !stomp.connected.value) return
  stomp.publish(`/app/tank/${roomId}/respawn`, { type: MessageType.ACTION, roomId, playerId: myPlayerId, payload: null })
}
function updateColor() {
  localStorage.setItem('brim_tank_color', selectedColor.value)
  stomp.publish(`/app/tank/${roomId}/color`, { type: MessageType.ACTION, roomId, playerId: myPlayerId, payload: { color: selectedColor.value } })
}
async function handleGoToLobby() {
  releaseInput()
  try { await leaveRoom(roomId, myPlayerId) } catch (error) { console.warn('Leave room error (non-fatal):', error) }
  finally { router.push('/') }
}
async function toggleFullscreen() {
  if (!arena.value) return
  try { if (document.fullscreenElement) await document.exitFullscreen(); else await arena.value.requestFullscreen() }
  catch { notify('Trình duyệt chưa cho phép toàn màn hình.') }
}
function playerName(id: string) { return id === myPlayerId ? `${session.player.username} · Bạn` : id.startsWith('bot-') ? 'Xe AI' : roomInfo.value?.players.find(player => player.id === id)?.username ?? id.slice(0, 8) }
function retryConnection() { window.location.reload() }
function fitCombatViewport() {
  if (!tankShell.value || !battleColumn.value) return
  const top = battleColumn.value.getBoundingClientRect().top - tankShell.value.getBoundingClientRect().top + tankShell.value.scrollTop
  battleColumn.value.style.setProperty('--combat-height', `${Math.max(0, window.innerHeight - top - 10)}px`)
}
watch(stomp.connected, connected => {
  if (connected) return
  // A disconnected player may miss a new-round signal. Never replay old actions.
  pendingCommand.value = null; pendingUpgrade.value = null
  waitingForRoundStart = false; awaitingRoundSnapshot = false; isRestarting.value = false
  awaitingFreshSnapshot.value = true
  releaseInput()
})
watch(canControl, enabled => { if (!enabled) releaseInput() })

onMounted(async () => {
  if (gameContainer.value) {
    game = new Phaser.Game({ type: Phaser.AUTO, parent: gameContainer.value, width: gameContainer.value.clientWidth,
      height: gameContainer.value.clientHeight, backgroundColor: '#17241d',
      scale: { mode: Phaser.Scale.RESIZE, autoCenter: Phaser.Scale.CENTER_BOTH }, scene: [TankScene] })
    game.events.on('ready', () => {
      const scene = game?.scene.getScene('TankScene') as TankScene
      if (scene) { tankScene = scene; scene.setPlayerId(myPlayerId); if (gameState.value) scene.updateGameState(gameState.value) }
    })
    const resizeObserver = new ResizeObserver(entries => {
      const bounds = entries[0]?.contentRect
      if (bounds && bounds.width > 0 && bounds.height > 0) game?.scale.resize(bounds.width, bounds.height)
      fitCombatViewport()
    })
    resizeObserver.observe(gameContainer.value)
    cleanupFns.push(() => resizeObserver.disconnect())
    window.addEventListener('resize', fitCombatViewport)
    cleanupFns.push(() => window.removeEventListener('resize', fitCombatViewport))
  }
  clockTimer = window.setInterval(() => {
    clock.value = Date.now()
    mouseShooting.value = !!tankScene?.getAim()?.shooting
    if (stomp.connected.value && alive.value) sendInput()
    if (pendingUpgrade.value && clock.value - pendingUpgrade.value.sentAt > 3500) { pendingUpgrade.value = null; notify('Chưa nhận xác nhận nâng cấp. Kiểm tra kết nối và tiếp tế rồi thử lại.') }
    if (isRestarting.value && clock.value - restartRequestedAt > 6000) {
      isRestarting.value = false; waitingForRoundStart = false; awaitingRoundSnapshot = false
      connectionError.value = 'Chưa nhận dữ liệu lượt mới. Kiểm tra kết nối rồi thử lại.'
    }
  }, 100)
  try { roomInfo.value = await getRoom(roomId) } catch (error) { console.warn('[TankView] Fetch room error:', error) }
  if (disposed) return
  try {
    await stomp.connect()
    if (disposed) return
    isConnecting.value = false
    cleanupFns.push(stomp.subscribe(`/topic/game/${roomId}/started`, (message: GameMessage) => {
      if ((message.timestamp ?? 0) < roundStartedAt) return
      roundStartedAt = message.timestamp ?? 0
      waitingForRoundStart = false; awaitingRoundSnapshot = true
      pendingCommand.value = null; pendingUpgrade.value = null; commandSeq = 0
      feedback.value = ''; gameState.value = null; isGameOver.value = false
      isRestarting.value = true; restartRequestedAt = Date.now(); releaseInput()
    }))
    cleanupFns.push(stomp.subscribe(`/topic/game/${roomId}/state/${myPlayerId}`, (state: TankGameState) => {
      if (waitingForRoundStart || (state.serverTime ?? 0) < roundStartedAt) return
      if ((state.serverTime ?? 0) < (gameState.value?.serverTime ?? 0)) return
      lastSnapshotAt.value = Date.now(); connectionError.value = ''; awaitingFreshSnapshot.value = false
      const previousTank = gameState.value?.tanks[myPlayerId]; gameState.value = state
      if (pendingCommand.value && (state.tanks[myPlayerId]?.tacticalView?.lastCommandSeq ?? 0) >= pendingCommand.value.commandSeq) {
        notify(tacticalMessages[state.tanks[myPlayerId]?.tacticalView?.commandStatus ?? ''] ?? 'Đã nhận lệnh.'); pendingCommand.value = null
      }
      if (pendingUpgrade.value) {
        const item = upgrades.value.find(item => item.branch === pendingUpgrade.value?.branch)
        if (item && item.level > pendingUpgrade.value.level) { notify(`Đã nâng ${item.label.toLowerCase()} lên cấp ${item.level}.`); pendingUpgrade.value = null }
      }
      if (previousTank?.tacticalView?.repairRemainingMs && !state.tanks[myPlayerId]?.tacticalView?.repairRemainingMs) notify((state.tanks[myPlayerId]?.health ?? 0) > previousTank.health ? 'Đã hoàn tất sửa xe.' : 'Sửa xe bị ngắt; bộ sửa đã được hoàn lại.')
      tankScene?.updateGameState(state); isGameOver.value = state.state === 'FINISHED'
      if (awaitingRoundSnapshot && state.state === 'IN_PROGRESS') { awaitingRoundSnapshot = false; isRestarting.value = false }
      if (isGameOver.value) gameOverMessage.value = assault.value ? 'Đội xe đã dừng bước' : 'Trận đấu đã kết thúc'
    }))
    cleanupFns.push(stomp.subscribe(`/topic/room/${roomId}/state`, (room: GameRoom) => { roomInfo.value = room; if (room.status === 'FINISHED') isGameOver.value = true }))
    cleanupFns.push(stomp.subscribe(`/topic/game/${roomId}/over`, () => { isGameOver.value = true; gameOverMessage.value = assault.value ? 'Đội xe đã dừng bước' : 'Trận đấu đã kết thúc' }))
    cleanupFns.push(stomp.subscribe(`/topic/game/${roomId}/error`, (error: { message: string }) => {
      connectionError.value = error.message; isRestarting.value = false; waitingForRoundStart = false; awaitingRoundSnapshot = false
    }))
    stomp.publish(`/app/tank/${roomId}/join`, { type: MessageType.JOIN_ROOM, roomId, playerId: myPlayerId,
      payload: { id: myPlayerId, username: session.player.username, color: selectedColor.value, sessionId: session.player.sessionId, score: 0, ready: true, state: 'PLAYING' } })
    readyTimer = window.setTimeout(() => { stomp.publish(`/app/tank/${roomId}/ready`, { type: MessageType.JOIN_ROOM, roomId, playerId: myPlayerId, payload: null }) }, 400)
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement
      if (target?.closest('input, select, textarea, [contenteditable="true"]') || (target?.closest('button, summary') && ['Space', 'Enter'].includes(event.code))) return
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space'].includes(event.code)) event.preventDefault()
      if (!event.repeat && event.code === 'KeyE') { issueCommand('INTERACT'); return }
      if (!event.repeat && event.code === 'KeyF') { issueCommand('REPAIR'); return }
      if (event.code === 'Escape') { releaseInput(); return }
      if (!canControl.value || pressedKeys.has(event.code)) return
      pressedKeys.add(event.code); updateInputState()
    }
    const onKeyUp = (event: KeyboardEvent) => { pressedKeys.delete(event.code); updateInputState() }
    const onVisibility = () => { if (document.hidden) releaseInput() }
    window.addEventListener('keydown', onKeyDown); window.addEventListener('keyup', onKeyUp)
    window.addEventListener('blur', releaseInput); document.addEventListener('visibilitychange', onVisibility)
    cleanupFns.push(() => { window.removeEventListener('keydown', onKeyDown); window.removeEventListener('keyup', onKeyUp); window.removeEventListener('blur', releaseInput); document.removeEventListener('visibilitychange', onVisibility) })
  } catch (error) { isConnecting.value = false; connectionError.value = 'Chưa kết nối được chiến trường. Vui lòng thử lại.'; console.error('[TankView] Stomp error:', error) }
})
onUnmounted(() => {
  disposed = true; releaseInput(); cleanupFns.forEach(cleanup => cleanup())
  if (clockTimer) window.clearInterval(clockTimer)
  if (readyTimer) window.clearTimeout(readyTimer)
  game?.destroy(true); game = null; tankScene = null
})
</script>

<template>
  <div ref="tankShell" class="tank-game">
    <header class="game-header">
      <div class="game-identity"><span class="tank-emblem" aria-hidden="true">⌖</span><div><span class="eyebrow">BRIM / TANK BATTLE</span><h1>{{ assault ? 'Mũi đột kích' : 'Đấu trường xe tăng' }}</h1></div><span class="mode-badge">{{ assault ? 'ASSAULT' : 'CLASSIC' }}</span></div>
      <nav class="header-actions" aria-label="Thao tác trận đấu"><button class="quiet-button" @click="handleGoToLobby">← Về sảnh</button><button class="quiet-button fullscreen-button" @click="toggleFullscreen">⛶ Toàn màn hình</button><label class="color-picker"><input v-model="selectedColor" type="color" aria-label="Chọn màu xe tăng" @change="updateColor" /><span>Màu xe</span></label></nav>
    </header>
    <section class="mission-banner" aria-label="Tình hình chiến trường">
      <div class="mission-title"><span class="eyebrow">{{ assault ? `GIẢ LẬP / ${operation.context.label}` : 'ĐỐI KHÁNG / TỰ DO' }}</span><strong>{{ assault ? operation.title : mapName }}</strong></div>
      <div v-if="assault" class="wave-summary"><strong>ĐỢT <span>{{ gameState?.wave || '—' }}</span></strong><p>{{ objectiveStatus }}</p></div>
      <div v-else class="wave-summary"><strong>{{ score }} <span>điểm</span></strong><p>Thu thập vật phẩm · giữ vị trí · hạ đối thủ</p></div>
      <div class="network-status" :class="networkState" role="status"><i aria-hidden="true" />{{ networkStatus }}</div>
    </section>
    <div v-if="connectionError" class="connection-error" role="alert"><span>{{ connectionError }}</span><button class="quiet-button" @click="retryConnection">Thử lại</button><button class="quiet-button" @click="router.push(`/room/${roomId}`)">Về phòng</button></div>
    <div class="battle-layout">
      <section ref="battleColumn" class="battle-column" aria-label="Chiến trường và tình trạng xe">
        <section class="resource-grid" :class="{ 'classic-resources': !assault }" aria-label="Tình trạng xe">
          <div class="resource-card" :class="{ danger: healthPercent <= 30 && !!myTank }"><div class="resource-heading"><span>TÌNH TRẠNG XE</span><b>{{ myTank ? `${Math.round(healthPercent)}%` : '—' }}</b></div><div class="resource-value">{{ myTank ? health : '—' }} <small>/ {{ maxHealth }} HP</small></div><progress :value="healthPercent" max="100" aria-label="Máu xe tăng" /><span class="resource-note">{{ !alive ? 'Xe đã bị phá hủy' : healthPercent <= 30 && myTank ? 'Hư hại nặng · tìm nơi che chắn' : assault ? 'Giữ giáp trước hướng về mối đe dọa' : 'Vật phẩm chữ thập giúp hồi máu' }}</span></div>
          <template v-if="assault">
            <div class="resource-card" :class="{ warning: tactical?.weapon.state === 'EMPTY' }"><div class="resource-heading"><span>PHÁO CHÍNH</span><b>{{ tactical?.weapon.state === 'LOADING' ? `${(tactical.weapon.remainingMs / 1000).toFixed(1)}s` : tactical?.weapon.state === 'READY' ? 'SẴN SÀNG' : '—' }}</b></div><div class="resource-value">{{ tactical?.weapon.chamber ?? '—' }} <small>trong nòng / {{ tactical?.weapon.reserve ?? '—' }} dự trữ</small></div><progress :value="reloadProgress" max="100" aria-label="Tiến độ nạp pháo" /><span class="resource-note">{{ weaponLabel }} · tự nạp 6 giây</span></div>
            <div class="resource-card" :class="{ warning: fuelPercent <= 20 && !!tactical }"><div class="resource-heading"><span>NHIÊN LIỆU</span><b>{{ tactical ? `${Math.ceil(fuelPercent)}%` : '—' }}</b></div><div class="resource-value">{{ tactical ? Math.round(Math.abs(tactical.speed)) : '—' }} <small>unit/s / {{ tactical?.settled ? 'Ngắm ổn định' : 'Chưa ổn định' }}</small></div><progress :value="fuelPercent" max="100" aria-label="Nhiên liệu còn lại" /><span class="resource-note">{{ tactical && fuelPercent <= 0 ? 'Hết nhiên liệu · tìm hòm tiếp tế' : 'Tiêu hao theo quãng đường di chuyển' }}</span></div>
          </template>
          <div v-else class="classic-effects"><span class="eyebrow">VẬT PHẨM TRÊN BẢN ĐỒ</span><p>✚ Hồi máu <span>◯ Khiên</span> <span>» Tăng tốc / bắn nhanh</span></p><div class="effect-chips"><span v-for="effect in activeEffects" :key="effect.label">{{ effect.label }} · {{ Math.ceil((effect.until - clock) / 1000) }}s</span><small v-if="!activeEffects.length">Đi qua vật phẩm để nhận hiệu ứng.</small></div></div>
        </section>
        <div ref="arena" class="canvas-wrapper" aria-label="Chiến trường xe tăng">
          <div ref="gameContainer" class="game-canvas" />
          <div class="arena-label"><span class="live-mark" />{{ mapName }}<small>{{ assault ? 'Vòng sáng: tầm bắn · dấu mờ: vị trí cũ' : 'WASD di chuyển · Space khai hỏa' }}</small></div>
          <div v-if="assault && mission" class="objective-hud" :class="{ secured: mission.secured, contested: mission.status === 'CONTESTED' }" aria-label="Mục tiêu hiện tại">
            <span>ĐIỂM {{ mission.sector }} / {{ mission.sectorCount }} <b>{{ mission.secured ? '✓ ĐÃ GIỮ' : objectiveDistance !== null ? `${objectiveDistance} unit` : '⌖' }}</b></span>
            <strong>{{ objectiveName }}</strong><progress :value="objectiveProgress" max="100" aria-label="Tiến độ giữ điểm" />
          </div>
          <div v-if="assault && nearbyWarning && alive" class="danger-hud" role="alert">⚠ Đạn nổ chậm · rời vòng cam!</div>
          <GameMinimap v-if="gameState" :state="gameState" game-type="TANK" :player-id="myPlayerId" />
          <div v-if="networkState !== 'online' && !isConnecting && !isGameOver" class="arena-network" role="status">{{ networkStatus }} · xe sẽ dừng khi mất input</div>
          <div v-if="isConnecting || (!gameState && !connectionError)" class="modal-overlay"><div class="spinner" /><h2>{{ isConnecting ? 'Kết nối đơn vị' : 'Đang chuẩn bị chiến trường' }}</h2><p>Đang chờ dữ liệu từ máy chủ…</p></div>
          <div v-else-if="isGameOver" class="modal-overlay"><div class="result-card"><span class="eyebrow">BÁO CÁO TRẬN ĐẤU</span><h2>{{ gameOverMessage || 'Trận đấu đã kết thúc' }}</h2><p>{{ assault ? 'Mỗi đợt hành quân là một cơ hội giữ đội xe đi xa hơn.' : 'Trở lại chiến trường để bắt đầu cuộc đấu mới.' }}</p><div class="result-stats"><div><strong>{{ score }}</strong><span>Điểm của bạn</span></div><div v-if="assault"><strong>{{ gameState?.wave || 0 }}</strong><span>Đợt đã tới</span></div></div><button v-if="isHost" class="primary-button" :disabled="!stomp.connected.value || isRestarting" @click="handleRestart">{{ isRestarting ? 'Đang chuẩn bị…' : 'Bắt đầu lượt mới' }}</button><p v-else>Chờ chủ phòng bắt đầu lượt tiếp theo.</p><button class="quiet-button" @click="router.push(`/room/${roomId}`)">Về phòng chờ</button></div></div>
          <div v-else-if="myTank && !alive" class="death-notice" role="status"><span class="eyebrow">XE ĐÃ BỊ PHÁ HỦY</span><h2>{{ assault ? 'Tiếp tục theo dõi đồng đội' : 'Sẵn sàng trở lại?' }}</h2><p>{{ assault ? 'Không hồi sinh trong lượt. Chủ phòng có thể bắt đầu một lượt mới.' : 'Hồi sinh đưa điểm về 0 và bắt đầu lượt xe mới.' }}</p><button v-if="!assault" class="primary-button" :disabled="respawnRemaining > 0 || !stomp.connected.value" @click="handleRespawn">{{ respawnRemaining > 0 ? `Hồi sinh sau ${respawnRemaining}s` : 'Trở lại chiến trường' }}</button><button v-else-if="isHost" class="quiet-button" :disabled="isRestarting || !stomp.connected.value" @click="handleRestart">{{ isRestarting ? 'Đang chuẩn bị…' : 'Bắt đầu lượt mới' }}</button></div>
          <span class="art-credit">KENNEY / CC0</span>
        </div>
        <div class="touch-controls" aria-label="Điều khiển cảm ứng">
          <div class="steering-pad"><button class="touch-button forward" :class="{ held: input.forward }" :disabled="!canControl" aria-label="Tiến" @pointerdown="holdControl($event, 'forward')" @pointerup="releaseControl('forward')" @pointercancel="releaseControl('forward')" @lostpointercapture="releaseControl('forward')">▲</button><button class="touch-button left" :class="{ held: input.turnLeft }" :disabled="!canControl" aria-label="Xoay trái" @pointerdown="holdControl($event, 'turnLeft')" @pointerup="releaseControl('turnLeft')" @pointercancel="releaseControl('turnLeft')" @lostpointercapture="releaseControl('turnLeft')">↶</button><button class="touch-button backward" :class="{ held: input.backward }" :disabled="!canControl" aria-label="Lùi" @pointerdown="holdControl($event, 'backward')" @pointerup="releaseControl('backward')" @pointercancel="releaseControl('backward')" @lostpointercapture="releaseControl('backward')">▼</button><button class="touch-button right" :class="{ held: input.turnRight }" :disabled="!canControl" aria-label="Xoay phải" @pointerdown="holdControl($event, 'turnRight')" @pointerup="releaseControl('turnRight')" @pointercancel="releaseControl('turnRight')" @lostpointercapture="releaseControl('turnRight')">↷</button></div>
          <p>{{ assault ? 'Chạm hoặc kéo trên chiến trường để ngắm.' : 'Giữ hướng di chuyển và nút khai hỏa.' }}</p><button class="touch-fire" :class="{ held: input.shoot }" :disabled="!canControl" @pointerdown="holdControl($event, 'shoot')" @pointerup="releaseControl('shoot')" @pointercancel="releaseControl('shoot')" @lostpointercapture="releaseControl('shoot')"><span>⌖</span>KHAI HỎA</button>
        </div>
        <div class="battle-footer"><span>{{ assault ? 'Đạn, nhiên liệu và hòm vật tư đều hữu hạn.' : 'W / S tiến lùi · A / D xoay thân · Space bắn' }}</span><button @click="helpOpen = !helpOpen">{{ helpOpen ? 'Ẩn hướng dẫn' : 'Hướng dẫn điều khiển' }} ↗</button></div>
      </section>
      <aside class="operations-panel" aria-label="Bảng điều hành xe tăng">
        <section v-if="assault && mission" class="panel-section mission-panel"><div class="section-heading"><h2>Mệnh lệnh hành quân</h2><span>{{ mission.sector }}/{{ mission.sectorCount }} điểm</span></div><ol class="mission-route"><li v-for="(name,index) in operation.sectors" :key="name" :class="{ current: index === mission.sector-1, complete: index < mission.sector-1 || index === mission.sector-1 && mission.secured }"><i>{{ index < mission.sector-1 || index === mission.sector-1 && mission.secured ? '✓' : index+1 }}</i><span>{{ name }}</span></li></ol><p class="mission-directive">{{ objectiveStatus }}</p><p>Đứng trong vòng cờ, không có địch để giữ điểm. Tiến độ được giữ khi rời vòng. Giữ điểm và dọn đợt địch để mở chặng tiếp theo.</p><div class="mission-reward"><b>+{{ mission.rewardCredits }} tiếp tế</b><span>Kho hữu hạn: 6 đạn · 400 nhiên liệu · 1 bộ sửa</span></div><p class="enemy-hint"><i aria-hidden="true">◉</i> Lính áo vàng bắn đạn nổ chậm. Vòng cam báo trước 1,4 giây; đổi vị trí hoặc tìm vật che chắn.</p></section>
        <details v-if="assault" class="panel-section history-panel"><summary><span>Hồ sơ lịch sử</span><b>{{ operation.context.era }}</b></summary><h3>{{ operation.context.title }}</h3><p>{{ operation.context.fact }}</p><a :href="operation.context.url" target="_blank" rel="noopener noreferrer">{{ operation.context.source }} ↗</a><p class="history-disclaimer">Địa hình, nhiệm vụ giữ điểm, đối thủ và thông số xe là sáng tác cho game; không tái hiện một trận đánh lịch sử hay lực lượng tại Dinh Độc Lập.</p></details>
        <section v-if="assault" class="panel-section observation-panel"><span class="eyebrow">QUAN SÁT CHIẾN TRƯỜNG</span><div class="contact-summary"><strong>{{ visibleContacts }}</strong><span>tiếp xúc đang thấy</span></div><p>{{ visibleContacts ? 'Địch chỉ hiện khi nằm trong tầm quan sát và không bị che khuất.' : 'Chưa có tiếp xúc trong tầm nhìn. Tiến lên thận trọng.' }}</p><small v-if="oldContacts">{{ oldContacts }} dấu vị trí cũ · tự mất sau 4 giây</small></section>
        <section v-if="assault" class="panel-section logistics-panel"><div class="section-heading"><h2>Hậu cần</h2><span>{{ tactical?.repairKits ?? '—' }} bộ sửa</span></div><button class="action-button" :disabled="!canSupply" @click="issueCommand('INTERACT')"><kbd>E</kbd><span><strong>{{ inventoryFull ? 'Đã đủ vật tư' : 'Nhận tiếp tế' }}</strong><small>{{ supplyLabel }}</small></span><b>↗</b></button><button class="action-button" :disabled="!canRepair" @click="issueCommand('REPAIR')"><kbd>F</kbd><span><strong>{{ repairing ? `Đang sửa · ${((tactical?.repairRemainingMs ?? 0) / 1000).toFixed(1)}s` : 'Sửa xe · 8 giây' }}</strong><small>{{ repairReason }}</small></span></button><progress v-if="repairing" class="repair-progress" :value="8000 - (tactical?.repairRemainingMs ?? 0)" max="8000" aria-label="Tiến độ sửa xe" /><p class="logistics-note">Di chuyển, bắn hoặc trúng đòn sẽ ngắt sửa và hoàn lại bộ sửa.</p></section>
        <div class="command-feedback" :class="{ 'has-feedback': commandNotice }" role="status" aria-live="polite">{{ commandNotice || 'Mọi thao tác được xác nhận từ máy chủ.' }}</div>
        <details v-if="assault" class="panel-section upgrades-panel" :open="upgradesOpen" @toggle="upgradesOpen = ($event.target as HTMLDetailsElement).open"><summary><span>Nâng cấp xe</span><b>{{ myTank?.credits ?? 0 }} tiếp tế</b></summary><p class="upgrade-hint">Tối đa 3 cấp mỗi nhánh · giá 100 / 200 / 300.</p><button v-for="item in upgrades" :key="item.branch" class="upgrade-card" :disabled="!canControl || item.level >= 3 || !item.affordable || !!pendingUpgrade" @click="upgrade(item.branch)"><span class="upgrade-symbol" aria-hidden="true">{{ item.symbol }}</span><span class="upgrade-copy"><strong>{{ item.label }} <small>{{ item.level }}/3</small></strong><span>{{ item.detail }}</span><span class="level-pips" aria-hidden="true"><i v-for="level in 3" :key="level" :class="{ filled: level <= item.level }" /></span></span><span class="upgrade-cost">{{ pendingUpgrade?.branch === item.branch ? 'Đang gửi…' : item.level >= 3 ? 'Tối đa' : `${item.cost}` }}<small v-if="item.level < 3 && pendingUpgrade?.branch !== item.branch">{{ item.affordable ? 'Nâng cấp ↑' : `Thiếu ${item.cost - (myTank?.credits ?? 0)}` }}</small></span></button><p class="upgrade-hint">Hạ lính +25, hạ tank +100, giữ điểm +40, qua đợt +75 tiếp tế.</p></details>
        <details class="panel-section controls-help" :open="helpOpen" @toggle="helpOpen = ($event.target as HTMLDetailsElement).open"><summary>Điều khiển & quy tắc <span>?</span></summary><dl><div><dt><kbd>W</kbd> <kbd>S</kbd></dt><dd>Tiến / lùi</dd></div><div><dt><kbd>A</kbd> <kbd>D</kbd></dt><dd>Xoay thân xe</dd></div><div><dt><kbd>Space</kbd></dt><dd>{{ assault ? 'Bắn theo hướng nòng' : 'Khai hỏa' }}</dd></div><template v-if="assault"><div><dt>Chuột / chạm</dt><dd>Chọn điểm ngắm</dd></div><div><dt><kbd>E</kbd> / <kbd>F</kbd></dt><dd>Nhận vật tư / sửa xe</dd></div></template></dl><p v-if="assault">Giữ chuột trái hoặc Space để bắn khi pháo sẵn sàng. Đứng yên 1,5 giây để ổn định ngắm. Tháp pháo cần thời gian xoay tới hướng chọn.</p><p v-if="assault" class="caution-note">Tầm bắn {{ tactical?.range ?? 700 }} unit. Vật cản chặn đạn và vụ nổ; bắn gần có thể gây sát thương cho chính xe bạn. Vòng sáng chỉ biểu thị tầm bắn.</p><p v-else>Khiên chặn một phát trong tối đa 10 giây. Tăng tốc và bắn nhanh tồn tại 6 giây. Có thể dùng phím mũi tên để lái.</p></details>
        <section class="panel-section roster"><div class="section-heading"><h2>{{ assault ? 'Đội xe trong vùng' : 'Xe trong vùng nhìn' }}</h2><span>{{ team.length }} xe</span></div><div v-for="tank in team" :key="tank.playerId" class="roster-row" :class="{ me: tank.playerId === myPlayerId, destroyed: !tank.alive }"><i :style="{ background: tank.color || '#8fa88a' }" aria-hidden="true" /><div><strong>{{ playerName(tank.playerId) }}</strong><span>{{ tank.alive ? `${tank.health} HP` : 'Đã bị phá hủy' }}</span></div><b>{{ tank.score }}<small>điểm</small></b></div><p v-if="!team.length" class="upgrade-hint">Đang chờ đội xe vào trận…</p></section>
        <details class="panel-section global-rank-panel" @toggle="rankOpen = ($event.target as HTMLDetailsElement).open"><summary>Bảng xếp hạng Tank</summary><GlobalLeaderboard v-if="rankOpen" game-type="TANK" class="global-rank-card" /></details>
        <button v-if="assault && isHost && !isGameOver" class="restart-button" :disabled="!stomp.connected.value || isRestarting" @click="handleRestart">{{ isRestarting ? 'Đang chuẩn bị lượt mới…' : '↻ Bắt đầu lượt mới' }}</button>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.objective-hud { position:absolute; z-index:9; right:14px; top:14px; width:220px; padding:10px 12px; background:#12221ded; border:1px solid #a18e51; border-radius:9px; pointer-events:none; box-shadow:0 5px 24px #0003 }
.objective-hud>span { display:flex; justify-content:space-between; gap:8px; color:#c6bb96; font-size:9px; letter-spacing:.8px }
.objective-hud>span b { letter-spacing:0; color:#e4c67a }
.objective-hud>strong { display:block; margin:5px 0 8px; font-size:13px }
.objective-hud progress { width:100%; height:4px; accent-color:#e4c67a }
.objective-hud.secured { border-color:#6f9d78 }
.objective-hud.secured>span b { color:#9cddad }
.objective-hud.contested { border-color:#c98461 }
.danger-hud { position:absolute; z-index:11; left:50%; top:110px; transform:translateX(-50%); width:max-content; max-width:calc(100% - 20px); border:1px solid #d89965; border-radius:7px; padding:9px 14px; background:#42251eee; color:#ffe1b9; font-size:12px; font-weight:700; pointer-events:none }
.mission-route { list-style:none; display:grid; gap:7px; padding:0; margin:12px 0 }
.mission-route li { display:flex; align-items:center; gap:9px; color:#92a296; font-size:11px }
.mission-route i { font-style:normal; display:grid; place-items:center; width:23px; height:23px; border:1px solid #425448; border-radius:50%; font-size:10px }
.mission-route .current { color:#f0d797; font-weight:650 }
.mission-route .current i { background:#c5ac581c; border-color:#aa9251 }
.mission-route .complete { color:#a2cda4 }
.mission-panel>p,.history-panel>p { font-size:11px; line-height:1.65; color:#a2b3a9; margin:9px 0 }
.mission-panel .mission-directive { color:#e4c67a; font-weight:650 }
.mission-reward { padding:10px 0; border-top:1px solid #304139; border-bottom:1px solid #304139 }
.mission-reward b { display:block; font-size:12px; color:#bad2a2 }
.mission-reward span { display:block; font-size:10px; color:#a2b3a9; margin-top:4px; line-height:1.6 }
.mission-panel .enemy-hint { color:#d6b895; margin-bottom:0 }
.enemy-hint i { font-style:normal; color:#e3b660; margin-right:4px }
.history-panel summary { display:flex; align-items:center; justify-content:space-between; gap:8px; cursor:pointer; font-size:12px; font-weight:650 }
.history-panel summary b { color:#cabb93; font-size:9px; font-weight:500 }
.history-panel h3 { font-size:13px; line-height:1.5; color:#e4c67a; margin:14px 0 7px }
.history-panel a { color:#b6cdb4; font-size:10px; line-height:1.6; text-underline-offset:3px }
.history-panel .history-disclaimer { font-size:10px; color:#93a195; padding-top:9px; border-top:1px solid #304139 }
@media(max-width:820px) { .mission-panel,.history-panel { grid-column:1/-1 }.mission-panel{order:0}.history-panel{order:4} }
@media(max-width:540px) {
  .objective-hud { top:auto; bottom:12px; right:9px; width:min(185px,calc(100% - 145px)); padding:8px }
  .objective-hud>span { font-size:8px; letter-spacing:0 }.objective-hud>strong { font-size:10px; margin:4px 0 }
  .danger-hud { top:70px; font-size:10px; padding:7px 10px }
  .mission-route { grid-template-columns:1fr 1fr 1fr; gap:6px }.mission-route li{align-items:flex-start;font-size:10px;line-height:1.5;flex-direction:column;gap:5px}
}
.tank-game {
  --tank-line:#304139;
  --tank-muted:#a2b3a9;
  --tank-gold:#e4c67a;
  color:#edf2e8;
  background:radial-gradient(ellipse at 30% 0,#24352a66,transparent 60%),#0d1714;
  width:100%;
  height:100dvh;
  padding:18px 22px 14px;
  display:flex;
  flex-direction:column;
  gap:14px;
  overflow:hidden;
  font-family:'Segoe UI',sans-serif
}

.tank-game * {
  box-sizing:border-box
}

button {
  font:inherit;
  cursor:pointer;
  color:inherit;
  transition:background .15s,border-color .15s
}

button:disabled {
  cursor:default
}

button:focus-visible,summary:focus-visible,input:focus-visible {
  outline:2px solid var(--tank-gold);
  outline-offset:4px
}

.game-header {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:15px;
  flex-shrink:0
}

.game-identity,.header-actions {
  display:flex;
  align-items:center;
  gap:12px
}

.tank-emblem {
  width:44px;
  height:44px;
  display:grid;
  place-items:center;
  color:var(--tank-gold);
  border:1px solid #766a46;
  border-radius:12px;
  background:#c5ac5820;
  font-size:30px
}

.eyebrow {
  display:block;
  color:var(--tank-muted);
  font-size:10px;
  font-weight:700;
  letter-spacing:1.7px;
  line-height:1.5
}

h1 {
  margin:2px 0 0;
  font-size:21px;
  font-weight:650;
  letter-spacing:-.5px
}

.mode-badge {
  color:var(--tank-gold);
  border:1px solid #6b6348;
  background:#c5ac5810;
  font-size:10px;
  letter-spacing:1px;
  font-weight:700;
  padding:5px 8px;
  border-radius:5px
}

.quiet-button {
  border:1px solid var(--tank-line);
  border-radius:7px;
  background:#ffffff05;
  padding:9px 12px;
  font-size:12px;
  color:#c6d0c9
}

.quiet-button:hover {
  background:#ffffff0c;
  border-color:#647967
}

.quiet-button:disabled {
  opacity:.4
}

.header-actions {
  gap:8px
}

.color-picker {
  display:flex;
  align-items:center;
  gap:7px;
  font-size:11px;
  color:var(--tank-muted);
  cursor:pointer;
  margin-left:3px
}

.color-picker input {
  border:0;
  padding:2px;
  width:28px;
  height:28px;
  background:#304139;
  border-radius:6px;
  cursor:pointer
}

.mission-banner {
  display:flex;
  align-items:center;
  gap:25px;
  border:1px solid var(--tank-line);
  border-left:3px solid #c3af70;
  border-radius:8px;
  background:linear-gradient(110deg,#223029,#17231f);
  padding:12px 17px;
  flex-shrink:0
}

.mission-title {
  min-width:180px
}

.mission-title strong {
  display:block;
  font-size:15px;
  margin-top:3px
}

.wave-summary {
  border-left:1px solid #3e4b3f;
  padding-left:25px;
  flex:1
}

.wave-summary strong {
  font-size:11px;
  color:#c2cdbd;
  letter-spacing:1px
}

.wave-summary strong>span {
  font-size:22px;
  color:var(--tank-gold);
  margin-left:4px;
  line-height:1
}

.wave-summary p {
  font-size:11px;
  color:var(--tank-muted);
  margin:4px 0 0
}

.network-status {
  display:flex;
  align-items:center;
  gap:7px;
  font-size:11px;
  white-space:nowrap;
  color:#b5c9b9
}

.network-status i,.live-mark {
  width:6px;
  height:6px;
  border-radius:50%;
  background:#a4d59d;
  display:inline-block
}

.network-status.offline {
  color:#f0b19b
}

.network-status.offline i {
  background:#ef936e
}

.network-status.slow {
  color:#e4c67a
}

.network-status.slow i {
  background:#e4c67a
}

.battle-layout {
  min-height:0;
  display:grid;
  grid-template-columns:minmax(0,1fr) 288px;
  gap:15px;
  flex:1
}

.battle-column {
  min-width:0;
  min-height:0;
  display:flex;
  flex-direction:column;
  gap:12px
}

.resource-grid {
  display:grid;
  grid-template-columns:repeat(3,minmax(0,1fr));
  gap:10px;
  flex-shrink:0
}

.resource-card {
  background:linear-gradient(130deg,#1c2a24,#17231f);
  border:1px solid var(--tank-line);
  border-radius:9px;
  padding:12px 13px 10px;
  min-width:0
}

.resource-heading {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:5px;
  font-size:9px;
  color:var(--tank-muted);
  letter-spacing:.7px
}

.resource-heading b {
  color:#cfdbbd;
  font-size:9px;
  letter-spacing:0;
  white-space:nowrap
}

.resource-value {
  margin:7px 0 8px;
  font-size:21px;
  line-height:1.2;
  font-weight:650;
  font-variant-numeric:tabular-nums
}

.resource-value small {
  font-size:11px;
  color:#b3c2b6;
  font-weight:400
}

progress {
  appearance:none;
  display:block;
  width:100%;
  height:4px;
  border:0;
  border-radius:4px;
  background:#334137;
  overflow:hidden;
  accent-color:#b6c493
}

progress::-webkit-progress-bar {
  background:#334137;
  border-radius:4px
}

progress::-webkit-progress-value {
  background:#b6c493;
  border-radius:4px
}

progress::-moz-progress-bar {
  background:#b6c493;
  border-radius:4px
}

.resource-note {
  color:#a2b3a9;
  display:block;
  font-size:10px;
  margin-top:7px;
  line-height:1.4
}

.resource-card.danger {
  border-color:#9a5c48
}

.resource-card.danger .resource-value,.resource-card.danger .resource-heading b {
  color:#ffb79e
}

.resource-card.danger progress::-webkit-progress-value {
  background:#e69172
}

.resource-card.warning {
  border-color:#786844
}

.resource-card.warning .resource-heading b {
  color:var(--tank-gold)
}

.classic-resources {
  grid-template-columns:minmax(200px,1fr) 2fr
}

.classic-effects {
  padding:13px 15px;
  border:1px solid var(--tank-line);
  border-radius:9px;
  background:#17231f
}

.classic-effects p {
  font-size:12px;
  margin:7px 0;
  color:#d3ddc8
}

.classic-effects p span {
  margin-left:13px
}

.effect-chips {
  display:flex;
  gap:6px;
  flex-wrap:wrap
}

.effect-chips span {
  font-size:10px;
  padding:3px 7px;
  border:1px solid #586a47;
  border-radius:5px;
  color:var(--tank-gold)
}

.effect-chips small {
  color:var(--tank-muted);
  font-size:10px
}

.canvas-wrapper {
  position:relative;
  flex:1;
  min-height:240px;
  border:1px solid #4a5946;
  border-radius:10px;
  overflow:hidden;
  background:#17241d;
  box-shadow:0 12px 40px #0003
}

.game-canvas {
  position:absolute;
  inset:0;
  touch-action:none
}

.game-canvas :deep(canvas) {
  display:block;
  touch-action:none
}

.arena-label {
  position:absolute;
  top:14px;
  left:15px;
  padding:8px 11px;
  background:#101a17d9;
  border:1px solid #5e71504d;
  border-radius:6px;
  z-index:4;
  pointer-events:none;
  color:#e1e7d5;
  font-size:11px
}

.arena-label .live-mark {
  margin-right:7px
}

.arena-label small {
  display:block;
  margin-top:4px;
  font-size:9px;
  color:#a9b8a4
}

.art-credit {
  position:absolute;
  bottom:10px;
  right:12px;
  font-size:8px;
  letter-spacing:1.7px;
  color:#d8decaaa;
  pointer-events:none
}

.arena-network {
  position:absolute;
  top:70px;
  left:50%;
  transform:translateX(-50%);
  max-width:90%;
  padding:9px 12px;
  border:1px solid #bc9b5e;
  border-radius:7px;
  background:#211c14ee;
  color:#f1d3a0;
  font-size:11px;
  z-index:11
}

.canvas-wrapper:fullscreen {
  width:100vw;
  height:100dvh;
  border:0;
  border-radius:0
}

.battle-footer {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
  font-size:10px;
  color:#8da394;
  flex-shrink:0
}

.battle-footer button {
  padding:0;
  color:#c8d2bd;
  background:none;
  border:0;
  font-size:10px;
  white-space:nowrap
}

.operations-panel {
  min-height:0;
  overflow:auto;
  display:flex;
  flex-direction:column;
  gap:11px;
  padding-right:3px;
  scrollbar-width:thin;
  scrollbar-color:#455e4b transparent
}

.panel-section {
  border:1px solid var(--tank-line);
  border-radius:9px;
  background:#17231f;
  padding:14px;
  flex-shrink:0
}

.contact-summary {
  display:flex;
  align-items:baseline;
  gap:10px;
  margin-top:5px
}

.contact-summary strong {
  font-size:30px;
  font-weight:500;
  color:var(--tank-gold);
  line-height:1.2
}

.contact-summary span {
  color:#d5dfcf;
  font-size:12px
}

.observation-panel p {
  font-size:11px;
  line-height:1.65;
  color:var(--tank-muted);
  margin:8px 0 0
}

.observation-panel small {
  font-size:10px;
  color:var(--tank-gold);
  display:block;
  margin-top:7px
}

.section-heading {
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin-bottom:11px;
  gap:8px
}

.section-heading h2 {
  margin:0;
  font-size:13px;
  font-weight:600
}

.section-heading>span {
  font-size:10px;
  color:var(--tank-muted)
}

.action-button {
  display:flex;
  align-items:center;
  gap:10px;
  width:100%;
  text-align:left;
  padding:10px;
  background:#26372a;
  border:1px solid #566648;
  border-radius:6px;
  margin-top:8px
}

.action-button:disabled {
  background:#1b2922;
  border-color:#33443a
}

.action-button:disabled kbd {
  opacity:.55
}

.action-button:disabled strong {
  color:#9caca0
}

.action-button>span {
  flex:1
}

.action-button strong {
  font-size:11px;
  color:#e1dfbd;
  display:block;
  font-weight:600
}

.action-button small {
  display:block;
  font-size:10px;
  margin-top:3px;
  color:#a1b09d;
  line-height:1.4
}

.action-button>b {
  font-size:16px;
  color:var(--tank-gold)
}

kbd {
  display:inline-grid;
  place-items:center;
  font-family:inherit;
  font-size:10px;
  min-width:23px;
  height:23px;
  border:1px solid #566b57;
  border-bottom-width:2px;
  border-radius:4px;
  color:#d6dfce;
  background:#1e3025;
  padding:0 4px
}

.logistics-note {
  font-size:10px;
  line-height:1.6;
  margin:10px 0 0;
  color:var(--tank-muted)
}

.repair-progress {
  margin-top:10px
}

.command-feedback {
  padding:1px 9px;
  min-height:15px;
  font-size:10px;
  color:#819a8a;
  line-height:1.6;
  flex-shrink:0
}

.command-feedback.has-feedback {
  padding:10px 12px;
  background:#293623;
  border:1px solid #62714b;
  border-radius:7px;
  color:#e0d9b4
}

summary {
  cursor:pointer;
  font-size:12px;
  font-weight:600;
  display:flex;
  justify-content:space-between;
  align-items:center;
  gap:8px;
  list-style:none
}

summary::-webkit-details-marker {
  display:none
}

summary::before {
  content:'›';
  color:#a5b594;
  font-size:16px
}

details[open]>summary::before {
  transform:rotate(90deg)
}

summary>span:first-child {
  flex:1
}

summary b {
  color:var(--tank-gold);
  font-size:10px;
  font-weight:500
}

.upgrade-hint {
  font-size:10px;
  line-height:1.6;
  color:var(--tank-muted);
  margin:11px 0 0
}

.upgrade-card {
  width:100%;
  display:flex;
  align-items:center;
  gap:10px;
  padding:11px 0;
  background:none;
  text-align:left;
  border:0;
  border-bottom:1px solid #304139
}

.upgrade-card:last-of-type {
  border-bottom:0
}

.upgrade-card:disabled .upgrade-cost {
  color:#95a38e
}

.upgrade-card:enabled:hover .upgrade-cost {
  color:#fff1ba
}

.upgrade-card:enabled:hover {
  background:#ffffff04
}

.upgrade-symbol {
  width:27px;
  height:30px;
  border:1px solid #4a5b44;
  background:#2c3926;
  border-radius:5px;
  display:grid;
  place-items:center;
  color:#d7cf9e;
  font-size:20px
}

.upgrade-copy {
  flex:1;
  min-width:0
}

.upgrade-copy strong {
  font-size:11px;
  display:block;
  color:#e0e6d6;
  font-weight:550
}

.upgrade-copy strong small {
  font-weight:400;
  font-size:10px;
  color:#a8b89f;
  margin-left:4px
}

.upgrade-copy>span:not(.level-pips) {
  font-size:9px;
  color:var(--tank-muted);
  display:block;
  margin:3px 0 5px
}

.level-pips {
  display:flex;
  gap:3px
}

.level-pips i {
  display:block;
  width:18px;
  height:3px;
  background:#39483a;
  border-radius:2px
}

.level-pips i.filled {
  background:#d6bd78
}

.upgrade-cost {
  font-size:13px;
  font-weight:600;
  color:var(--tank-gold);
  text-align:right
}

.upgrade-cost small {
  font-size:9px;
  font-weight:400;
  margin-top:3px;
  display:block
}

.controls-help summary>span {
  font-size:10px;
  color:var(--tank-muted)
}

.controls-help dl {
  margin:12px 0
}

.controls-help dl>div {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
  margin:7px 0;
  font-size:10px
}

.controls-help dt {
  color:#c5d0bc
}

.controls-help dd {
  margin:0;
  color:var(--tank-muted)
}

.controls-help p {
  font-size:10px;
  line-height:1.7;
  margin:9px 0 0;
  color:var(--tank-muted)
}

.controls-help .caution-note {
  color:#dbbf83;
  border-left:2px solid #8b784d;
  padding-left:9px
}

.roster-row {
  display:flex;
  align-items:center;
  gap:8px;
  padding:10px 0;
  border-bottom:1px solid #2c3a32
}

.roster-row:last-child {
  border-bottom:0;
  padding-bottom:0
}

.roster-row>i {
  width:7px;
  height:7px;
  border-radius:2px;
  flex-shrink:0
}

.roster-row>div {
  flex:1;
  min-width:0
}

.roster-row strong {
  display:block;
  font-size:11px;
  font-weight:500;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap
}

.roster-row.me strong {
  color:#ded6a8
}

.roster-row span {
  display:block;
  font-size:10px;
  color:#a0b29f;
  margin-top:3px
}

.roster-row>b {
  font-size:12px;
  font-weight:500;
  color:#d8c38d;
  text-align:right
}

.roster-row>b small {
  display:block;
  font-size:9px;
  color:#8fa18e;
  font-weight:400
}

.roster-row.destroyed span {
  color:#db9b84
}

.restart-button {
  flex-shrink:0;
  border:1px dashed #64745a;
  border-radius:7px;
  color:#bec9b3;
  background:transparent;
  padding:11px;
  font-size:11px
}

.restart-button:disabled {
  opacity:.4
}

.modal-overlay {
  position:absolute;
  inset:0;
  z-index:15;
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  background:#0b1511dd;
  backdrop-filter:blur(5px);
  padding:22px;
  text-align:center;
  overflow:auto
}

.modal-overlay h2 {
  font-size:20px;
  font-weight:600;
  margin:15px 0 8px
}

.modal-overlay>p {
  font-size:12px;
  color:var(--tank-muted)
}

.result-card {
  max-width:370px;
  width:100%
}

.result-card>p {
  font-size:12px;
  line-height:1.7;
  color:#b4c3af
}

.result-stats {
  display:flex;
  justify-content:center;
  gap:45px;
  border-top:1px solid #566b574d;
  border-bottom:1px solid #566b574d;
  margin:22px 0;
  padding:17px 0
}

.result-stats strong {
  font-size:30px;
  color:var(--tank-gold);
  display:block;
  font-weight:500
}

.result-stats span {
  font-size:11px;
  color:#aebda7
}

.result-card>button {
  width:100%;
  margin-top:8px
}

.primary-button {
  color:#1e2718;
  background:#d4c084;
  border:1px solid #e0cd94;
  border-radius:6px;
  padding:11px 16px;
  font-size:12px;
  font-weight:650
}

.primary-button:hover {
  background:#e2d09b
}

.primary-button:disabled {
  opacity:.45
}

.death-notice {
  position:absolute;
  top:14px;
  right:14px;
  z-index:12;
  max-width:300px;
  padding:16px;
  border:1px solid #96734d;
  border-radius:9px;
  background:#192019f2;
  box-shadow:0 10px 35px #0005
}

.death-notice h2 {
  font-size:16px;
  margin:7px 0
}

.death-notice p {
  color:#b6bda4;
  font-size:11px;
  line-height:1.65
}

.death-notice>button {
  width:100%
}

.death-notice .eyebrow {
  color:#d4b482
}

.connection-error {
  flex-shrink:0;
  display:flex;
  gap:10px;
  align-items:center;
  padding:9px 13px;
  border:1px solid #986b49;
  background:#3d2c1f;
  border-radius:7px;
  font-size:12px;
  color:#f4d2ad
}

.connection-error>span {
  flex:1
}

.spinner {
  width:34px;
  height:34px;
  border:2px solid #cfc18422;
  border-top-color:#d4c084;
  border-radius:50%;
  animation:spin 1s linear infinite
}

@keyframes spin {
  to {
    transform:rotate(360deg)
  }
}

.touch-controls {
  display:none
}

@media(min-width:1600px) {
  .tank-game {
    padding:23px 30px 18px;
    gap:18px
  }
  .battle-layout {
    grid-template-columns:minmax(0,1fr) 315px;
    gap:18px
  }
  .resource-card {
    padding:15px 16px 12px
  }
  .resource-value {
    font-size:25px
  }
  .resource-note {
    font-size:11px
  }
  .resource-heading {
    font-size:10px
  }
  .panel-section {
    padding:16px
  }
}

@media(max-width:1100px) {
  .tank-game {
    padding:13px;
    gap:10px
  }
  .battle-layout {
    grid-template-columns:minmax(0,1fr) 255px;
    gap:10px
  }
  .resource-card {
    padding:10px
  }
  .resource-value {
    font-size:18px
  }
  .resource-value small {
    font-size:10px
  }
  .resource-note {
    font-size:9px
  }
  .resource-heading {
    font-size:8px;
    letter-spacing:.2px
  }
  .resource-heading b {
    font-size:8px
  }
  .mission-title {
    min-width:150px
  }
  .color-picker span {
    display:none
  }
  .game-identity {
    gap:9px
  }
  .mode-badge {
    display:none
  }
}

@media(max-width:820px),(max-height:500px) {
  .tank-game {
    overflow:auto;
    height:100dvh;
    padding:12px;
    gap:12px
  }
  .battle-layout {
    display:flex;
    flex-direction:column;
    flex:0 0 auto
  }
  .battle-column {
    gap:10px
  }
  .canvas-wrapper {
    flex:none;
    height:52dvh;
    min-height:320px
  }
  .operations-panel {
    display:grid;
    grid-template-columns:repeat(2,minmax(0,1fr));
    overflow:visible;
    padding:0;
    align-items:start;
    gap:10px
  }
  .observation-panel {
    grid-column:1
  }
  .logistics-panel {
    grid-column:2;
    grid-row:1 / span 2
  }
  .command-feedback {
    grid-column:1
  }
  .upgrades-panel {
    grid-column:1 / -1
  }
  .controls-help {
    grid-column:1
  }
  .roster {
    grid-column:2
  }
  .restart-button {
    grid-column:1 / -1
  }
  .mission-banner {
    gap:15px;
    padding:11px 13px
  }
  .mission-title {
    min-width:120px
  }
  .wave-summary {
    padding-left:15px
  }
  .network-status {
    font-size:10px
  }
  .header-actions .fullscreen-button {
    display:none
  }
}

@media(pointer:coarse),(max-width:700px) {
  .touch-controls {
    display:flex;
    align-items:center;
    justify-content:space-between;
    gap:15px;
    padding:8px 12px;
    border:1px solid var(--tank-line);
    border-radius:8px;
    background:#17231f;
    flex-shrink:0
  }
  .steering-pad {
    display:grid;
    grid-template-columns:repeat(3,44px);
    grid-template-rows:44px 44px;
    gap:4px
  }
  .touch-button,.touch-fire {
    touch-action:none;
    user-select:none;
    -webkit-user-select:none;
    border:1px solid #53644b;
    background:#273a2a;
    border-radius:6px;
    color:#dce2c8
  }
  .touch-button {
    padding:0;
    font-size:16px
  }
  .forward {
    grid-column:2
  }
  .left {
    grid-column:1;
    grid-row:2
  }
  .backward {
    grid-column:2;
    grid-row:2
  }
  .right {
    grid-column:3;
    grid-row:2
  }
  .touch-controls>p {
    color:#adbea8;
    font-size:10px;
    line-height:1.6;
    max-width:180px;
    text-align:center
  }
  .touch-fire {
    min-width:80px;
    min-height:72px;
    font-size:9px;
    font-weight:650;
    letter-spacing:.4px;
    background:#4a4c2e;
    border-color:#978653
  }
  .touch-fire>span {
    display:block;
    font-size:28px;
    line-height:1.3
  }
  .touch-button.held,.touch-fire.held {
    background:#64764b;
    border-color:#d2c18a
  }
  .touch-button:disabled,.touch-fire:disabled {
    opacity:.4
  }
}

@media(max-width:540px) {
  .tank-game {
    padding:10px;
    gap:10px
  }
  .game-header {
    gap:7px
  }
  .tank-emblem {
    width:34px;
    height:36px;
    font-size:24px;
    border-radius:8px
  }
  .eyebrow {
    font-size:8px;
    letter-spacing:1.2px
  }
  h1 {
    font-size:17px
  }
  .header-actions {
    gap:5px
  }
  .header-actions .quiet-button {
    font-size:10px;
    padding:8px
  }
  .color-picker {
    margin-left:0
  }
  .color-picker input {
    width:25px;
    height:27px
  }
  .mission-banner {
    flex-wrap:wrap;
    gap:9px 13px;
    padding:10px 12px
  }
  .mission-title {
    flex:1;
    min-width:100px
  }
  .mission-title strong {
    font-size:13px
  }
  .wave-summary {
    flex:1;
    padding-left:13px
  }
  .wave-summary p {
    font-size:9px
  }
  .wave-summary strong {
    font-size:9px
  }
  .wave-summary strong>span {
    font-size:19px
  }
  .network-status {
    width:100%;
    padding-top:7px;
    border-top:1px solid #ffffff0c;
    font-size:9px
  }
  .resource-grid {
    gap:5px
  }
  .resource-card {
    padding:9px 7px;
    border-radius:7px
  }
  .resource-heading {
    display:block;
    font-size:8px
  }
  .resource-heading b {
    display:block;
    margin-top:3px;
    font-size:8px
  }
  .resource-value {
    font-size:16px;
    margin:6px 0
  }
  .resource-value small {
    display:block;
    font-size:9px;
    margin-top:3px;
    line-height:1.5;
    min-height:24px
  }
  .resource-note {
    font-size:9px;
    min-height:24px;
    line-height:1.5
  }
  .canvas-wrapper {
    height:49dvh;
    min-height:310px;
    border-radius:8px
  }
  .arena-label {
    top:9px;
    left:9px;
    padding:6px 8px;
    font-size:10px
  }
  .arena-label small {
    font-size:8px;
    max-width:210px
  }
  .canvas-wrapper :deep(.game-minimap) {
    left:8px;
    bottom:10px;
    transform:scale(.62)
  }
  .operations-panel {
    grid-template-columns:1fr
  }
  .operations-panel>.panel-section,.operations-panel>.command-feedback,.operations-panel>.restart-button {
    grid-column:1;
    grid-row:auto
  }
  .logistics-panel {
    order:0
  }
  .command-feedback {
    order:1
  }
  .observation-panel {
    order:2
  }
  .upgrades-panel {
    order:3
  }
  .controls-help {
    order:4
  }
  .roster {
    order:5
  }
  .restart-button {
    order:6
  }
  .panel-section {
    padding:13px
  }
  .logistics-panel {
    display:grid;
    grid-template-columns:1fr 1fr;
    gap:8px
  }
  .logistics-panel .section-heading,.logistics-panel .logistics-note,.logistics-panel .repair-progress {
    grid-column:1 / -1
  }
  .logistics-panel .section-heading {
    margin-bottom:0
  }
  .action-button {
    margin-top:0;
    padding:8px;
    gap:6px
  }
  .action-button strong {
    font-size:10px
  }
  .action-button small {
    font-size:9px
  }
  .action-button kbd {
    min-width:20px;
    height:21px
  }
  .action-button>b {
    display:none
  }
  .logistics-note {
    margin:0
  }
  .touch-controls {
    padding:8px;
    gap:9px
  }
  .touch-controls>p {
    font-size:9px
  }
  .battle-footer {
    font-size:8px;
    align-items:flex-start
  }
  .battle-footer button {
    font-size:9px
  }
  .classic-resources {
    grid-template-columns:1fr 1.5fr
  }
  .classic-effects {
    padding:10px
  }
  .classic-effects p {
    font-size:10px;
    line-height:1.8;
    margin:5px 0
  }
  .classic-effects p span {
    margin-left:5px
  }
  .effect-chips small {
    font-size:9px
  }
  .death-notice {
    top:64px;
    right:9px;
    max-width:240px;
    padding:12px
  }
  .death-notice h2 {
    font-size:14px
  }
  .death-notice p {
    font-size:10px
  }
  .result-card h2 {
    font-size:18px
  }
  .result-card>p {
    font-size:11px
  }
  .result-stats {
    margin:15px 0;
    padding:12px 0
  }
  .connection-error {
    flex-wrap:wrap
  }
  .connection-error>span {
    flex-basis:100%
  }
  .modal-overlay {
    padding:16px
  }
}

@media(max-height:650px) and (min-width:821px) {
  .tank-game {
    padding:8px 12px;
    gap:8px
  }
  .mission-banner {
    padding:8px 12px
  }
  .resource-card {
    padding:8px 10px
  }
  .resource-value {
    margin:4px 0;
    font-size:17px
  }
  .resource-note {
    margin-top:4px;
    font-size:9px
  }
  .canvas-wrapper {
    min-height:180px
  }
  .battle-column {
    gap:7px
  }
  .game-identity .tank-emblem {
    width:35px;
    height:35px
  }
  h1 {
    font-size:18px
  }
}

@media (max-width: 540px) and (min-height: 600px) {
  .battle-column {
    height: var(--combat-height, calc(100dvh - 160px));
    min-height: 0;
  }
  .canvas-wrapper {
    height: auto;
    min-height: 180px;
    flex: 1;
  }
}
@media (max-width: 540px) and (min-height: 600px) and (max-height: 700px) {
  .resource-note {
    display: none;
  }
  .resource-value small {
    min-height: 20px;
  }
  .battle-column {
    gap: 8px;
  }
}
.global-rank-card {
  width: 100%;
  margin-top: 12px;
  padding: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  backdrop-filter: none;
}
.global-rank-panel {
  grid-column: 1 / -1;
}
@media (max-width: 540px) {
  .global-rank-panel {
    order: 6;
  }
  .restart-button {
    order: 7;
  }
}
@media(prefers-reduced-motion:reduce) {
  * {
    transition:none!important
  }
  .spinner {
    animation-duration:2s
  }
}
</style>
