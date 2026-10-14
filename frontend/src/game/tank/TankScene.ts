import Phaser from 'phaser'
import type { TankGameState } from '@/types/gameState'

const TANK_SIZE = 28
const TANK_COLORS = ['#2ecc71', '#3498db', '#9b59b6', '#e67e22', '#1abc9c', '#e74c3c']

export class TankScene extends Phaser.Scene {
  private graphics!: Phaser.GameObjects.Graphics
  private playerId: string
  private lastState: TankGameState | null = null
  private renderedPositions = new Map<string, { x: number; y: number; angle: number }>()
  private cameraReady = false

  constructor() {
    super({ key: 'TankScene' })
    this.playerId = crypto.randomUUID()
  }

  create() {
    this.graphics = this.add.graphics()
  }

  updateGameState(state: TankGameState) {
    this.lastState = state
    this.cameras.main.setBounds(0, 0, state.mapWidth, state.mapHeight)
  }

  setPlayerId(id: string) {
    this.playerId = id
  }

  override update() {
    if (!this.lastState) return
    this.graphics.clear()
    this.updateCamera(this.lastState)
    this.drawBackground(this.lastState)
    this.drawWalls(this.lastState)
    this.drawTanks(this.lastState)
    this.drawBullets(this.lastState)
  }

  private drawBackground(state: TankGameState) {
    this.graphics.fillStyle(0x090f1f, 1)
    this.graphics.fillRect(0, 0, state.mapWidth, state.mapHeight)
    this.graphics.lineStyle(1, 0x17243d, .5)
    for (let x = 0; x < state.mapWidth; x += 100) this.graphics.lineBetween(x, 0, x, state.mapHeight)
    for (let y = 0; y < state.mapHeight; y += 100) this.graphics.lineBetween(0, y, state.mapWidth, y)
  }

  private drawWalls(state: TankGameState) {
    for (const wall of state.walls) {
      this.graphics.fillStyle(0x050811, .35)
      this.graphics.fillRoundedRect(wall.x + 5, wall.y + 7, wall.width, wall.height, 5)
      this.graphics.fillStyle(0x40516a, 1)
      this.graphics.fillRoundedRect(wall.x, wall.y, wall.width, wall.height, 5)
      this.graphics.lineStyle(2, 0x637894, .75)
      this.graphics.strokeRoundedRect(wall.x, wall.y, wall.width, wall.height, 5)
    }
  }

  private drawTanks(state: TankGameState) {
    let colorIdx = 0
    for (const [id, tank] of Object.entries(state.tanks)) {
      const isMe = id === this.playerId
      const color = isMe ? 0x2ecc71 : parseInt(TANK_COLORS[colorIdx % TANK_COLORS.length].slice(1), 16)
      colorIdx++

      if (!tank.alive) {
        this.graphics.fillStyle(0x555555, 0.3)
      } else {
        this.graphics.fillStyle(color, 1)
      }

      let rendered = this.renderedPositions.get(id) ?? { x: tank.x, y: tank.y, angle: tank.angle }
      if (Phaser.Math.Distance.Between(rendered.x, rendered.y, tank.x, tank.y) > 350) {
        rendered = { x: tank.x, y: tank.y, angle: tank.angle }
      }
      rendered.x = Phaser.Math.Linear(rendered.x, tank.x, .35)
      rendered.y = Phaser.Math.Linear(rendered.y, tank.y, .35)
      rendered.angle = Phaser.Math.Angle.RotateTo(rendered.angle, tank.angle, .12)
      this.renderedPositions.set(id, rendered)

      this.graphics.save()
      this.graphics.translateCanvas(rendered.x, rendered.y)
      this.graphics.rotateCanvas(rendered.angle)
      // Tracks and armored body.
      this.graphics.fillStyle(0x111827, tank.alive ? 1 : .35)
      this.graphics.fillRoundedRect(-TANK_SIZE / 2, -TANK_SIZE / 2 - 4, TANK_SIZE, 7, 3)
      this.graphics.fillRoundedRect(-TANK_SIZE / 2, TANK_SIZE / 2 - 3, TANK_SIZE, 7, 3)
      this.graphics.fillStyle(color, tank.alive ? 1 : .3)
      this.graphics.fillRoundedRect(-TANK_SIZE / 2 + 2, -TANK_SIZE / 2, TANK_SIZE - 4, TANK_SIZE, 6)
      this.graphics.fillStyle(0x0f172a, .35)
      this.graphics.fillCircle(0, 0, 8)
      // Turret and barrel.
      this.graphics.fillStyle(isMe ? 0x27ae60 : color, 1)
      this.graphics.fillRoundedRect(0, -3.5, TANK_SIZE * 0.92, 7, 3)
      this.graphics.fillCircle(0, 0, 7)
      this.graphics.restore()

      if (tank.alive) {
        this.graphics.fillStyle(0x050811, .8)
        this.graphics.fillRoundedRect(rendered.x - 18, rendered.y - 25, 36, 5, 2)
        this.graphics.fillStyle(tank.health > 30 ? 0x4ade80 : 0xfb7185, 1)
        this.graphics.fillRoundedRect(rendered.x - 18, rendered.y - 25, 36 * tank.health / 100, 5, 2)
        if (isMe) {
          this.graphics.lineStyle(2, 0x67e8f9, .8)
          this.graphics.strokeCircle(rendered.x, rendered.y, 22)
        }
      }
    }
  }

  private updateCamera(state: TankGameState) {
    const tank = state.tanks[this.playerId]
    if (!tank) return
    const rendered = this.renderedPositions.get(this.playerId) ?? tank
    const camera = this.cameras.main
    camera.setZoom(Phaser.Math.Linear(camera.zoom, 1, .04))
    const snap = !this.cameraReady || Phaser.Math.Distance.Between(rendered.x, rendered.y, tank.x, tank.y) > 350
    if (snap) {
      camera.centerOn(tank.x, tank.y)
      this.cameraReady = true
      return
    }
    camera.centerOn(
      Phaser.Math.Linear(camera.midPoint.x, rendered.x, .12),
      Phaser.Math.Linear(camera.midPoint.y, rendered.y, .12)
    )
  }

  private drawBullets(state: TankGameState) {
    for (const bullet of state.bullets) {
      this.graphics.fillStyle(0xfbbf24, .2)
      this.graphics.fillCircle(bullet.x, bullet.y, 9)
      this.graphics.fillStyle(0xfff1a8, 1)
      this.graphics.fillCircle(bullet.x, bullet.y, 4)
    }
  }
}

export { TANK_COLORS, TANK_SIZE }
