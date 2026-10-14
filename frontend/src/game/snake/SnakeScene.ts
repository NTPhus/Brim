import Phaser from 'phaser'
import type { SnakeFood, SnakeGameState, SnakePosition } from '@/types/gameState'

const SNAKE_COLORS = [0x2ecc71, 0x3498db, 0x9b59b6, 0xe67e22, 0x1abc9c, 0xe74c3c]

export class SnakeScene extends Phaser.Scene {
  private graphics!: Phaser.GameObjects.Graphics
  private playerId: string = crypto.randomUUID()
  private targetState: SnakeGameState | null = null
  private renderBodies = new Map<string, SnakePosition[]>()
  private cameraReady = false

  constructor() { super({ key: 'SnakeScene' }) }

  create() { this.graphics = this.add.graphics() }

  updateGameState(state: SnakeGameState) {
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
    this.drawArena(state.gridWidth, state.gridHeight)
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
      for (let i = 0; i < rendered.length; i++) {
        rendered[i].x = Phaser.Math.Linear(rendered[i].x, snake.body[i].x, 0.32)
        rendered[i].y = Phaser.Math.Linear(rendered[i].y, snake.body[i].y, 0.32)
      }
    }
  }

  private updateCamera(state: SnakeGameState) {
    const me = state.snakes[this.playerId]
    const body = this.renderBodies.get(this.playerId)
    if (!me?.alive || !body?.[0]) return
    const desiredZoom = Phaser.Math.Clamp(1.32 - Math.log2(Math.max(18, me.maxLength) / 18) * 0.16, 0.52, 1.32)
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

  private drawArena(width: number, height: number) {
    this.graphics.fillStyle(0x070b17, 1)
    this.graphics.fillRect(0, 0, width, height)
    this.graphics.lineStyle(1, 0x17213b, 0.42)
    for (let x = 0; x <= width; x += 100) this.graphics.lineBetween(x, 0, x, height)
    for (let y = 0; y <= height; y += 100) this.graphics.lineBetween(0, y, width, y)
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
    let colorIndex = 0
    for (const [id, snake] of Object.entries(state.snakes)) {
      const body = this.renderBodies.get(id)
      if (!body?.length) continue
      const color = id === this.playerId ? 0x38e88f : SNAKE_COLORS[++colorIndex % SNAKE_COLORS.length]
      const alpha = snake.alive ? 1 : 0.18
      const thickness = Phaser.Math.Clamp(13 + Math.log2(Math.max(18, snake.maxLength) / 18) * 2.2, 13, 25)

      this.graphics.lineStyle(thickness + 5, 0x05070e, alpha * .55)
      this.strokeBody(body)
      this.graphics.lineStyle(thickness, color, alpha)
      this.strokeBody(body)

      const head = body[0]
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
