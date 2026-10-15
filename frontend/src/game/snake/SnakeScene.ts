import { createId } from '@/utils/id'
import Phaser from 'phaser'
import type { SnakeFood, SnakeGameState, SnakePosition } from '@/types/gameState'

const SNAKE_COLORS = [0x2ecc71, 0x3498db, 0x9b59b6, 0xe67e22, 0x1abc9c, 0xe74c3c]

export class SnakeScene extends Phaser.Scene {
  private graphics!: Phaser.GameObjects.Graphics
  private playerId: string = createId()
  private targetState: SnakeGameState | null = null
  private renderBodies = new Map<string, SnakePosition[]>()
  private cameraReady = false

  constructor() { super({ key: 'SnakeScene' }) }

  create() { this.graphics = this.add.graphics() }

  updateGameState(state: SnakeGameState) {
    for (const id of this.renderBodies.keys()) if (!state.snakes[id]) this.renderBodies.delete(id)
    this.targetState = state
    this.cameras.main.setBounds(0, 0, state.gridWidth, state.gridHeight)
  }

  setPlayerId(id: string) { this.playerId = id }

  screenToWorld(clientX: number, clientY: number, rect: DOMRect) {
    const localX = (clientX - rect.left) * (this.scale.width / rect.width)
    const localY = (clientY - rect.top) * (this.scale.height / rect.height)
    return this.cameras.main.getWorldPoint(localX, localY)
  }

  override update() {
    const state = this.targetState
    if (!state) return
    this.interpolateBodies(state)
    this.updateCamera(state)
    this.graphics.clear()
    this.drawArena()
    this.drawFood(state.food)
    this.drawSnakes(state)
  }

  private interpolateBodies(state: SnakeGameState) {
    for (const [id, snake] of Object.entries(state.snakes)) {
      let rendered = this.renderBodies.get(id)
      const teleported = rendered?.[0] && Phaser.Math.Distance.Between(rendered[0].x, rendered[0].y, snake.body[0].x, snake.body[0].y) > 500
      if (!rendered || teleported || Math.abs(rendered.length - snake.body.length) > 8) {
        rendered = snake.body.map(point => ({ ...point }))
        this.renderBodies.set(id, rendered)
        if (id === this.playerId && teleported) this.cameraReady = false
      }
      while (rendered.length < snake.body.length) rendered.push({ ...snake.body[rendered.length] })
      while (rendered.length > snake.body.length) rendered.pop()
      const interpolationStride = rendered.length > 500 ? 2 : 1
      for (let i = 0; i < rendered.length; i += interpolationStride) {
        rendered[i].x = Phaser.Math.Linear(rendered[i].x, snake.body[i].x, 0.32)
        rendered[i].y = Phaser.Math.Linear(rendered[i].y, snake.body[i].y, 0.32)
      }
    }
  }

  private updateCamera(state: SnakeGameState) {
    const me = state.snakes[this.playerId]
    const body = this.renderBodies.get(this.playerId)
    if (!me?.alive || !body?.[0]) return
    const desiredZoom = Math.max(this.scale.width / 3200, this.scale.height / 2000, Phaser.Math.Clamp(1.32 - Math.log2(Math.max(18, me.maxLength) / 18) * 0.16, 0.52, 1.32))
    const camera = this.cameras.main
    camera.setZoom(Phaser.Math.Linear(camera.zoom, desiredZoom, 0.045))
    if (!this.cameraReady) {
      camera.centerOn(body[0].x, body[0].y)
      this.cameraReady = true
    } else {
      camera.centerOn(
        Phaser.Math.Linear(camera.midPoint.x, body[0].x, 0.12),
        Phaser.Math.Linear(camera.midPoint.y, body[0].y, 0.12)
      )
    }
  }

  private drawArena() {
    const width = this.targetState?.gridWidth ?? 0
    const height = this.targetState?.gridHeight ?? 0
    const view = Phaser.Geom.Rectangle.Inflate(Phaser.Geom.Rectangle.Clone(this.cameras.main.worldView), 120, 120)
    this.graphics.fillStyle(0x070b17, 1)
    this.graphics.fillRect(view.x, view.y, view.width, view.height)
    this.graphics.lineStyle(1, 0x17213b, 0.42)
    for (let x = Math.floor(view.x / 100) * 100; x <= view.right; x += 100) this.graphics.lineBetween(x, view.y, x, view.bottom)
    for (let y = Math.floor(view.y / 100) * 100; y <= view.bottom; y += 100) this.graphics.lineBetween(view.x, y, view.right, y)
    this.graphics.lineStyle(8, 0x405174, 1)
    this.graphics.strokeRect(4, 4, width - 8, height - 8)
  }

  private drawFood(food: SnakeFood[]) {
    const cameraView = this.cameras.main.worldView
    const view = Phaser.Geom.Rectangle.Inflate(
      new Phaser.Geom.Rectangle(cameraView.x, cameraView.y, cameraView.width, cameraView.height), 80, 80
    )
    food.forEach((pellet, index) => {
      if (!view.contains(pellet.x, pellet.y)) return
      const color = SNAKE_COLORS[(index + pellet.value) % SNAKE_COLORS.length]
      this.graphics.fillStyle(color, 0.16)
      this.graphics.fillCircle(pellet.x, pellet.y, pellet.radius * 1.9)
      this.graphics.fillStyle(color, 1)
      this.graphics.fillCircle(pellet.x, pellet.y, pellet.radius)
      if (pellet.value >= 8) {
        this.graphics.fillStyle(0xffffff, 0.75)
        this.graphics.fillCircle(pellet.x - pellet.radius * .25, pellet.y - pellet.radius * .25, pellet.radius * .22)
      }
    })
  }

  private drawSnakes(state: SnakeGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(Phaser.Geom.Rectangle.Clone(this.cameras.main.worldView), 120, 120)
    let colorIndex = 0
    for (const [id, snake] of Object.entries(state.snakes)) {
      const body = this.renderBodies.get(id)
      if (!body?.length) continue
      const color = snake.color && /^#[0-9a-fA-F]{6}$/.test(snake.color)
        ? parseInt(snake.color.slice(1), 16)
        : (id === this.playerId ? 0x38e88f : SNAKE_COLORS[++colorIndex % SNAKE_COLORS.length])
      const alpha = snake.alive ? 1 : 0.18
      const thickness = Phaser.Math.Clamp(13 + Math.log2(Math.max(18, snake.maxLength) / 18) * 3.6, 13, 42)

      // Long bodies are sampled and off-screen snakes are skipped to keep redraws cheap
      // while preserving the smooth head and nearby body motion.
      if (!view.contains(body[0].x, body[0].y) && !body.some(point => view.contains(point.x, point.y))) continue
      const stride = body.length > 500 ? 2 : 1
      const visibleBody = body.filter((point, index) => index % stride === 0 || view.contains(point.x, point.y))
      if (visibleBody.length < 2) continue
      this.graphics.lineStyle(thickness + 5, 0x05070e, alpha * .55)
      this.strokeBody(visibleBody)
      this.graphics.lineStyle(thickness, color, alpha)
      this.strokeBody(visibleBody)
      // Round the tube with small overlapping caps so the body does not look square.
      this.graphics.fillStyle(color, alpha)
      const capStep = Math.max(2, Math.ceil(visibleBody.length / 180))
      for (let i = 0; i < visibleBody.length; i += capStep) {
        const segment = visibleBody[i]
        this.graphics.fillCircle(segment.x, segment.y, thickness * .5)
      }
      this.graphics.lineStyle(Math.max(2, thickness * .22), 0xffffff, alpha * .24)
      this.strokeBody(visibleBody)

      const detailStep = Math.max(8, Math.floor(visibleBody.length / 36))
      this.graphics.fillStyle(0xffffff, alpha * .16)
      for (let i = detailStep; i < visibleBody.length; i += detailStep) {
        const segment = visibleBody[i]
        this.graphics.fillCircle(segment.x, segment.y, Math.max(2, thickness * .16))
      }

      const head = body[0]
      if (snake.boosting) {
        this.graphics.lineStyle(3, 0xfbbf24, .45)
        this.graphics.strokeCircle(head.x, head.y, thickness * .95)
      }
      this.graphics.fillStyle(color, alpha)
      this.graphics.fillCircle(head.x, head.y, thickness * .58)
      if (!snake.alive) continue
      const sideX = Math.cos(snake.angle + Math.PI / 2) * thickness * .24
      const sideY = Math.sin(snake.angle + Math.PI / 2) * thickness * .24
      const frontX = Math.cos(snake.angle) * thickness * .28
      const frontY = Math.sin(snake.angle) * thickness * .28
      this.graphics.fillStyle(0xffffff, 1)
      this.graphics.fillCircle(head.x + frontX + sideX, head.y + frontY + sideY, thickness * .16)
      this.graphics.fillCircle(head.x + frontX - sideX, head.y + frontY - sideY, thickness * .16)
      this.graphics.fillStyle(0x111111, 1)
      this.graphics.fillCircle(head.x + frontX * 1.18 + sideX, head.y + frontY * 1.18 + sideY, thickness * .075)
      this.graphics.fillCircle(head.x + frontX * 1.18 - sideX, head.y + frontY * 1.18 - sideY, thickness * .075)
    }
  }

  private strokeBody(body: SnakePosition[]) {
    this.graphics.beginPath()
    this.graphics.moveTo(body[0].x, body[0].y)
    for (let i = 1; i < body.length; i++) this.graphics.lineTo(body[i].x, body[i].y)
    this.graphics.strokePath()
  }
}

export { SNAKE_COLORS }
