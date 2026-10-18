import { createId } from '@/utils/id'
import Phaser from 'phaser'
import { WorldEffects } from '@/game/WorldEffects'
import type { TankGameState } from '@/types/gameState'

const TANK_SIZE = 28
const TANK_COLORS = ['#2ecc71', '#3498db', '#9b59b6', '#e67e22', '#1abc9c', '#e74c3c']

export class TankScene extends Phaser.Scene {
  private graphics!: Phaser.GameObjects.Graphics
  private playerId: string
  private lastState: TankGameState | null = null
  private renderedPositions = new Map<string, { x: number; y: number; angle: number }>()
  private cameraReady = false
  private terrain!: Phaser.GameObjects.TileSprite
  private sprites = new Map<string, Phaser.GameObjects.Container>()
  private projectileSprites: Phaser.GameObjects.Image[] = []
  private previousHealth = new Map<string, number>()
  private smoothing = .3
  private effects=new WorldEffects()
  private motionPreference=window.matchMedia('(prefers-reduced-motion: reduce)')
  private cameraSmoothing=.12
  private zoomSmoothing=.04
  private bulletOrigins:Array<{x:number;y:number}>=[]
  private snapshotAt=0
  private snapshotInterval=50

  preload() {
    const base = '/assets/kenney-tanks/'
    for (const [key, file] of Object.entries({
      hull: 'tankBeige_outline.png', turret: 'barrelBeige_outline.png',
      terrain: 'dirt.png', projectile: 'bulletYellowSilver_outline.png', smoke: 'smokeOrange0.png'
    })) this.load.image(`tank-${key}`, base + file)
  }

  constructor() {
    super({ key: 'TankScene' })
    this.playerId = createId()
  }

  create() {
    this.terrain = this.add.tileSprite(0, 0, 1, 1, 'tank-terrain').setOrigin(0).setDepth(-10).setTint(0x737e78)
    this.graphics = this.add.graphics().setDepth(5)
    this.events.once('shutdown', () => {
      this.sprites.clear(); this.renderedPositions.clear(); this.previousHealth.clear()
      this.projectileSprites = []
      this.effects.clear();this.lastState=null;this.cameraReady=false;this.bulletOrigins=[];this.snapshotAt=0
    })
  }

  updateGameState(state: TankGameState) {
    if(state.serverTime!==undefined&&state.serverTime===this.lastState?.serverTime)return
    const used=new Set<number>()
    this.bulletOrigins=state.bullets.map(bullet=>{
      let match=-1,best=Infinity
      this.lastState?.bullets.forEach((old,index)=>{
        const age=old.life-bullet.life
        if(!used.has(index)&&old.ownerId===bullet.ownerId&&Math.abs(old.angle-bullet.angle)<.001&&age>0&&age<=5&&age<best){match=index;best=age}
      })
      if(match>=0){
        used.add(match)
        const rendered=this.projectileSprites[match]
        return rendered?{x:rendered.x,y:rendered.y}:{x:this.lastState!.bullets[match].x,y:this.lastState!.bullets[match].y}
      }
      if(this.lastState)this.effects.emit(bullet.x,bullet.y,0xffdd8a,12)
      return {x:bullet.x,y:bullet.y}
    })
    this.snapshotInterval=Phaser.Math.Clamp(this.time.now-this.snapshotAt,16,150)
    this.snapshotAt=this.time.now
    for (const id of this.renderedPositions.keys()) if (!state.tanks[id]) this.renderedPositions.delete(id)
    for (const [id, sprite] of this.sprites) {
      if (!state.tanks[id]) { sprite.destroy(); this.sprites.delete(id); this.previousHealth.delete(id) }
    }
    for (const [id, tank] of Object.entries(state.tanks)) {
      const previous = this.previousHealth.get(id)
      if (previous !== undefined && tank.health < previous) {
        this.effects.emit(tank.x,tank.y,tank.alive?0xffc078:0xff855b,tank.alive?28:65)
        if (id === this.playerId&&!this.motionPreference.matches) this.cameras.main.shake(80, .002)
        if (!tank.alive&&!this.motionPreference.matches) {
          const smoke = this.add.image(tank.x, tank.y, 'tank-smoke').setDepth(8).setScale(.45)
          this.tweens.add({ targets: smoke, scale: 1.3, alpha: 0, duration: 600, onComplete: () => smoke.destroy() })
        }
      }
      if(previous!==undefined&&tank.health>previous&&this.lastState?.tanks[id]?.alive&&tank.alive)this.effects.emit(tank.x,tank.y,0x84e6a5,35)
      this.previousHealth.set(id, tank.health)
    }
    this.lastState = state
    this.cameras.main.setBounds(0, 0, state.mapWidth, state.mapHeight)
  }

  setPlayerId(id: string) {
    this.playerId = id
  }

  override update(_time: number, delta: number) {
    this.smoothing = 1 - Math.exp(-Math.min(delta, 100) / 45)
    this.cameraSmoothing=this.motionPreference.matches?1:1-Math.exp(-Math.min(delta,100)/130)
    this.zoomSmoothing=this.motionPreference.matches?1:1-Math.exp(-Math.min(delta,100)/400)
    if(this.motionPreference.matches)this.cameras.main.shakeEffect.reset()
    if (!this.lastState) return
    this.graphics.clear()
    this.updateCamera(this.lastState)
    this.drawBackground()
    this.drawWalls(this.lastState)
    this.drawHealthPacks(this.lastState)
    this.drawTanks(this.lastState)
    this.drawBullets(this.lastState)
    this.effects.draw(this.graphics,delta)
  }

  private drawBackground() {
    const view = this.cameras.main.worldView
    this.terrain.setPosition(view.x, view.y).setSize(view.width + 2, view.height + 2)
    this.terrain.tilePositionX = view.x
    this.terrain.tilePositionY = view.y
  }

  private drawWalls(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(Phaser.Geom.Rectangle.Clone(this.cameras.main.worldView), 80, 80)
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
    const cameraView = this.cameras.main.worldView
    const view = new Phaser.Geom.Rectangle(cameraView.x - 100, cameraView.y - 100, cameraView.width + 200, cameraView.height + 200)
    for (const [id, tank] of Object.entries(state.tanks)) {
      const isMe = id === this.playerId
      const color = /^#[0-9a-fA-F]{6}$/.test(tank.color ?? '') ? parseInt((tank.color ?? '#8fa88a').slice(1), 16) : 0x8fa88a
      let rendered = this.renderedPositions.get(id) ?? { x: tank.x, y: tank.y, angle: tank.angle }
      if (Phaser.Math.Distance.Between(rendered.x, rendered.y, tank.x, tank.y) > 350) {
        rendered = { x: tank.x, y: tank.y, angle: tank.angle }
      }
      rendered.x = Phaser.Math.Linear(rendered.x, tank.x, this.smoothing)
      rendered.y = Phaser.Math.Linear(rendered.y, tank.y, this.smoothing)
      rendered.angle += Phaser.Math.Angle.Wrap(tank.angle - rendered.angle) * this.smoothing
      this.renderedPositions.set(id, rendered)
      let sprite = this.sprites.get(id)
      if (!sprite) {
        const shadow = this.add.image(4, 4, 'tank-hull').setDisplaySize(36, 40).setTint(0x000000).setAlpha(.3)
        const hull = this.add.image(0, 0, 'tank-hull').setDisplaySize(36, 40)
        const turret = this.add.image(0, 1, 'tank-turret').setOrigin(.5, 1).setDisplaySize(11, 30)
        sprite = this.add.container(rendered.x, rendered.y, [shadow, hull, turret]).setDepth(2)
        this.sprites.set(id, sprite)
      }
      sprite.setPosition(rendered.x, rendered.y).setRotation(rendered.angle + Math.PI / 2)
        .setAlpha(tank.alive ? 1 : .28).setVisible(view.contains(rendered.x, rendered.y))
      ;(sprite.getAt(1) as Phaser.GameObjects.Image).setTint(color)
      ;(sprite.getAt(2) as Phaser.GameObjects.Image).setTint(color)
      if (!tank.alive || !sprite.visible) continue
      this.graphics.fillStyle(0x101a16, .9).fillRoundedRect(rendered.x - 21, rendered.y - 34, 42, 6, 3)
      this.graphics.fillStyle(tank.health > 30 ? 0x84e6a5 : 0xff8071)
        .fillRoundedRect(rendered.x - 20, rendered.y - 33, 40 * Math.max(0, tank.health) / 100, 4, 2)
      if ((tank.shieldUntil ?? 0) > Date.now()) this.graphics.lineStyle(3, 0x7cd9fa, .8).strokeCircle(rendered.x, rendered.y, 30)
      if (isMe) this.graphics.lineStyle(2, 0xc4f7e5, .8).strokeCircle(rendered.x, rendered.y, 26)
    }
  }

  private updateCamera(state: TankGameState) {
    const tank = state.tanks[this.playerId]
    if (!tank) return
    const rendered = this.renderedPositions.get(this.playerId) ?? tank
    const camera = this.cameras.main
    camera.setZoom(Phaser.Math.Linear(camera.zoom, Math.max(1, this.scale.width / 2200, this.scale.height / 1400), this.zoomSmoothing))
    const snap = !this.cameraReady || Phaser.Math.Distance.Between(rendered.x, rendered.y, tank.x, tank.y) > 350
    if (snap) {
      camera.centerOn(tank.x, tank.y)
      this.cameraReady = true
      return
    }
    camera.centerOn(
      Phaser.Math.Linear(camera.midPoint.x, rendered.x, this.cameraSmoothing),
      Phaser.Math.Linear(camera.midPoint.y, rendered.y, this.cameraSmoothing)
    )
  }

  private drawBullets(state: TankGameState) {
    const progress=Phaser.Math.Clamp((this.time.now-this.snapshotAt)/this.snapshotInterval,0,1)
    state.bullets.forEach((bullet, index) => {
      let sprite = this.projectileSprites[index]
      if (!sprite) {
        sprite = this.add.image(0, 0, 'tank-projectile').setDisplaySize(8, 14).setDepth(3)
        this.projectileSprites.push(sprite)
      }
      const origin=this.bulletOrigins[index]??bullet
      sprite.setPosition(Phaser.Math.Linear(origin.x,bullet.x,progress),Phaser.Math.Linear(origin.y,bullet.y,progress))
        .setRotation(bullet.angle + Math.PI / 2).setVisible(true)
    })
    for (let index = state.bullets.length; index < this.projectileSprites.length; index++) this.projectileSprites[index].setVisible(false)
  }

  private drawHealthPacks(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(Phaser.Geom.Rectangle.Clone(this.cameras.main.worldView), 60, 60)
    for (const item of state.healthPacks ?? []) {
      if (!view.contains(item.x, item.y)) continue
      const type = item.type ?? 'HEALTH'
      const color = { HEALTH: 0x4ade80, SHIELD: 0x67d8ff, SPEED: 0xfcd56b, RAPID_FIRE: 0xd8a1ff }[type]
      this.graphics.fillStyle(color, .18).fillCircle(item.x, item.y, item.radius * 1.8)
      this.graphics.fillStyle(0x162622).fillRoundedRect(item.x - 13, item.y - 13, 26, 26, 6)
      this.graphics.lineStyle(2, color).strokeRoundedRect(item.x - 13, item.y - 13, 26, 26, 6)
      this.graphics.fillStyle(color)
      if (type === 'HEALTH') {
        this.graphics.fillRect(item.x - 3, item.y - 8, 6, 16).fillRect(item.x - 8, item.y - 3, 16, 6)
      } else if (type === 'SHIELD') {
        this.graphics.lineStyle(3, color).strokeCircle(item.x, item.y, 7)
      } else {
        this.graphics.lineStyle(3, color)
        for (const offset of [-4, 3]) {
          this.graphics.lineBetween(item.x + offset - 3, item.y - 7, item.x + offset + 3, item.y)
          this.graphics.lineBetween(item.x + offset + 3, item.y, item.x + offset - 3, item.y + 7)
        }
      }
    }
  }
}

export { TANK_COLORS, TANK_SIZE }
