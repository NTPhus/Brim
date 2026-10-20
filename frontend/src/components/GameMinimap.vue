<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { SnakeGameState, TankGameState } from '@/types/gameState'

const props = defineProps<{
  state: SnakeGameState | TankGameState | null
  gameType: 'SNAKE' | 'TANK'
  playerId: string
}>()

const canvas = ref<HTMLCanvasElement | null>(null)
let frame = 0

function draw() {
  const element = canvas.value
  const state = props.state
  if (!element || !state) return
  const width = 190
  const height = 125
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  element.width = width * dpr
  element.height = height * dpr
  const context = element.getContext('2d')
  if (!context) return
  context.setTransform(dpr, 0, 0, dpr, 0, 0)
  context.clearRect(0, 0, width, height)
  context.fillStyle = 'rgba(5, 10, 24, .92)'
  context.fillRect(0, 0, width, height)

  const worldWidth = props.gameType === 'SNAKE' ? (state as SnakeGameState).gridWidth : (state as TankGameState).mapWidth
  const worldHeight = props.gameType === 'SNAKE' ? (state as SnakeGameState).gridHeight : (state as TankGameState).mapHeight
  const sx = (x: number) => x / worldWidth * width
  const sy = (y: number) => y / worldHeight * height

  context.strokeStyle = 'rgba(103, 232, 249, .45)'
  context.lineWidth = 1
  context.strokeRect(.5, .5, width - 1, height - 1)

  if ('snakes' in state) {
    for (const [id, snake] of Object.entries(state.snakes)) {
      if (!snake.body.length) continue
      context.beginPath()
      context.strokeStyle = id === props.playerId ? '#67e8f9' : 'rgba(148, 163, 184, .7)'
      context.lineWidth = id === props.playerId ? 2.4 : 1.2
      const step = Math.max(1, Math.floor(snake.body.length / 80))
      for (let i = 0; i < snake.body.length; i += step) {
        const point = snake.body[i]
        if (i === 0) context.moveTo(sx(point.x), sy(point.y))
        else context.lineTo(sx(point.x), sy(point.y))
      }
      context.stroke()
      const head = snake.body[0]
      context.fillStyle = id === props.playerId ? '#f8fafc' : '#94a3b8'
      context.fillRect(sx(head.x) - 2, sy(head.y) - 2, 4, 4)
    }
    for (const pellet of state.food) {
      context.fillStyle = pellet.value >= 8 ? '#fbbf24' : '#4ade80'
      context.fillRect(sx(pellet.x) - 1, sy(pellet.y) - 1, pellet.value >= 8 ? 3 : 2, pellet.value >= 8 ? 3 : 2)
    }
  } else {
    for (const wall of state.walls) {
      context.fillStyle = 'rgba(100, 116, 139, .7)'
      context.fillRect(sx(wall.x), sy(wall.y), wall.width / worldWidth * width, wall.height / worldHeight * height)
    }
    for (const item of state.healthPacks ?? []) {
      context.fillStyle = '#4ade80'
      context.fillRect(sx(item.x) - 2, sy(item.y) - 2, 4, 4)
    }
    for (const supply of state.supplies ?? []) {
      context.fillStyle = supply.quantity > 0 ? '#e2c074' : '#626855'
      context.fillRect(sx(supply.x) - 2, sy(supply.y) - 2, 4, 4)
    }
    if (state.mode === 'ASSAULT' && state.mission) {
      const mission = state.mission
      context.strokeStyle = mission.secured ? '#9cddad' : '#e4c67a'
      context.lineWidth = 1.5
      context.beginPath(); context.ellipse(sx(mission.x), sy(mission.y), Math.max(4,mission.radius/worldWidth*width),Math.max(4,mission.radius/worldHeight*height),0,0,Math.PI*2); context.stroke()
      context.fillStyle = context.strokeStyle; context.font = 'bold 9px sans-serif'; context.textAlign = 'center'
      context.fillText(mission.secured ? '✓' : String(mission.sector),sx(mission.x),sy(mission.y)+3)
    }
    for (const contact of state.contacts ?? []) if (!contact.current) {
      context.strokeStyle = '#bbaa8277'; context.strokeRect(sx(contact.x) - 2, sy(contact.y) - 2, 4, 4)
    }
    for (const enemy of state.enemyMarkers ?? []) {
      context.fillStyle = '#fb8067'
      context.fillRect(sx(enemy.x) - 1.5, sy(enemy.y) - 1.5, 3, 3)
    }
    for (const [id, tank] of Object.entries(state.tanks)) {
      context.fillStyle = id === props.playerId ? '#67e8f9' : (tank.alive ? '#fbbf24' : '#64748b')
      context.fillRect(sx(tank.x) - 2, sy(tank.y) - 2, 5, 5)
    }
  }
}

watch(() => props.state, () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(draw) })
onMounted(() => nextTick(draw))
onUnmounted(() => cancelAnimationFrame(frame))
</script>

<template>
  <aside class="game-minimap" aria-label="Bản đồ thu nhỏ">
    <div class="minimap-label">MAP</div>
    <canvas ref="canvas" class="minimap-canvas" />
  </aside>
</template>

<style scoped>
.game-minimap { position: absolute; z-index: 9; left: 1rem; bottom: 1rem; width: 190px; padding: .35rem; border-radius: 12px; background: rgba(5, 10, 24, .72); border: 1px solid rgba(103, 232, 249, .25); box-shadow: 0 8px 24px rgba(0,0,0,.35); backdrop-filter: blur(8px); pointer-events: none; }
.minimap-label { padding: 0 .25rem .2rem; color: #a5f3fc; font-size: .58rem; font-weight: 800; letter-spacing: .14em; }
.minimap-canvas { display: block; width: 190px; height: 125px; border-radius: 8px; }
@media (max-width: 700px) { .game-minimap { transform: scale(.78); transform-origin: bottom left; } }
</style>
