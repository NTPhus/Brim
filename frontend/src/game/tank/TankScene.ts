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
    this.drawBackground()
    this.drawWalls(this.lastState)
    this.drawHealthPacks(this.lastState)
    this.drawTanks(this.lastState)
    this.drawBullets(this.lastState)
  }

  private drawBackground() {
    const view = Phaser.Geom.Rectangle.Inflate(this.cameras.main.worldView, 120, 120)
    this.graphics.fillStyle(0x090f1f, 1)
    this.graphics.fillRect(view.x, view.y, view.width, view.height)
    this.graphics.lineStyle(1, 0x17243d, .5)
    for (let x = Math.floor(view.x / 100) * 100; x <= view.right; x += 100) this.graphics.lineBetween(x, view.y, x, view.bottom)
    for (let y = Math.floor(view.y / 100) * 100; y <= view.bottom; y += 100) this.graphics.lineBetween(view.x, y, view.right, y)
  }

  private drawWalls(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(this.cameras.main.worldView, 80, 80)
    for (const wall of state.walls) {
      if (!Phaser.Geom.Rectangle.Overlaps(view, new Phaser.Geom.Rectangle(wall.x, wall.y, wall.width, wall.height))) continue
      this.graphics.fillStyle(0x050811, .35)
      this.graphics.fillRoundedRect(wall.x + 5, wall.y + 7, wall.width, wall.height, 5)
      this.graphics.fillStyle(0x40516a, 1)
      this.graphics.fillRoundedRect(wall.x, wall.y, wall.width, wall.height, 5)
      this.graphics.fillStyle(0x536b86, .55)
      this.graphics.fillRoundedRect(wall.x + 3, wall.y + 3, Math.max(1, wall.width - 6), Math.max(1, wall.height - 6), 4)
      this.graphics.lineStyle(2, 0x637894, .75)
      this.graphics.strokeRoundedRect(wall.x, wall.y, wall.width, wall.height, 5)
      this.graphics.lineStyle(1, 0x9fb3c8, .28)
      this.graphics.lineBetween(wall.x + 8, wall.y + 8, wall.x + wall.width - 8, wall.y + wall.height - 8)
    }
  }

  private drawTanks(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(this.cameras.main.worldView, 100, 100)
    let colorIdx = 0
    for (const [id, tank] of Object.entries(state.tanks)) {
      const isMe = id === this.playerId
      if (!view.contains(tank.x, tank.y) && tank.alive) continue
      const color = tank.color && /^#[0-9a-fA-F]{6}$/.test(tank.color)
        ? parseInt(tank.color.slice(1), 16)
        : (isMe ? 0x2ecc71 : parseInt(TANK_COLORS[colorIdx % TANK_COLORS.length].slice(1), 16))
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
    const view = Phaser.Geom.Rectangle.Inflate(this.cameras.main.worldView, 60, 60)
    for (const bullet of state.bullets) {
      if (!view.contains(bullet.x, bullet.y)) continue
      this.graphics.fillStyle(0xfbbf24, .2)
      this.graphics.fillCircle(bullet.x, bullet.y, 9)
      this.graphics.fillStyle(0xfff1a8, 1)
      this.graphics.fillCircle(bullet.x, bullet.y, 4)
    }
  }

  private drawHealthPacks(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(this.cameras.main.worldView, 60, 60)
    for (const item of state.healthPacks ?? []) {
      if (!view.contains(item.x, item.y)) continue
      this.graphics.fillStyle(0x22c55e, .18)
      this.graphics.fillCircle(item.x, item.y, item.radius * 1.8)
      this.graphics.fillStyle(0x16a34a, 1)
      this.graphics.fillCircle(item.x, item.y, item.radius)
      this.graphics.fillStyle(0xffffff, 1)
      this.graphics.fillRect(item.x - 3, item.y - 8, 6, 16)
      this.graphics.fillRect(item.x - 8, item.y - 3, 16, 6)
    }
  }
}

export { TANK_COLORS, TANK_SIZE }
