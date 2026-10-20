import { createId } from '@/utils/id'
import Phaser from 'phaser'
import { WorldEffects } from '@/game/WorldEffects'
import { shellImpact, tankMap, tankOperation } from './maps'
import type { TankGameState } from '@/types/gameState'

const TANK_SIZE = 28
const TANK_COLORS = ['#2ecc71', '#3498db', '#9b59b6', '#e67e22', '#1abc9c', '#e74c3c']

export class TankScene extends Phaser.Scene {
  private graphics!: Phaser.GameObjects.Graphics
  private ground!: Phaser.GameObjects.Graphics
  private objectiveLabel!: Phaser.GameObjects.Text
  private battlefieldKey = ''
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
  private mouseAim: { x: number; y: number } | null = null
  private touchAim: { x: number; y: number } | null = null
  private mouseShooting = false

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
    this.ground = this.add.graphics().setDepth(-8)
    this.objectiveLabel = this.add.text(0, 0, '', { fontFamily: 'Segoe UI, sans-serif', fontSize: '15px', fontStyle: 'bold', color: '#f0d797', backgroundColor: '#15231fe8', padding: { x: 10, y: 6 } }).setOrigin(.5).setDepth(6).setVisible(false)
    this.input.addPointer(2)
    const aimPointer = (pointer: Phaser.Input.Pointer) => {
      if (pointer.wasTouch) {
        if (!pointer.isDown) return
        const point = this.cameras.main.getWorldPoint(pointer.x, pointer.y)
        this.touchAim = { x: point.x, y: point.y }
        this.mouseAim = null
      } else {
        this.mouseAim = { x: pointer.x, y: pointer.y }
        this.touchAim = null
      }
    }
    this.input.on('pointermove', aimPointer)
    this.input.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
      aimPointer(pointer)
      // Touch selects an aim point. The separate HUD button controls firing.
      if (!pointer.wasTouch && pointer.button === 0) this.mouseShooting = true
    })
    this.input.on('pointerup', () => this.releasePointer())
    this.input.on('pointerupoutside', () => this.releasePointer())
    this.input.on('gameout', () => this.releasePointer())
    this.events.once('shutdown', () => {
      this.sprites.clear(); this.renderedPositions.clear(); this.previousHealth.clear()
      this.projectileSprites = []
      this.effects.clear();this.lastState=null;this.cameraReady=false;this.bulletOrigins=[];this.snapshotAt=0
      this.mouseAim=null;this.touchAim=null;this.mouseShooting=false;this.battlefieldKey=''
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
        if (id === this.playerId) {
          const label = this.add.text(tank.x, tank.y - 50, `−${previous - tank.health}`, { fontSize: '18px', fontStyle: 'bold', color: '#ffb49b', stroke: '#14221b', strokeThickness: 4 }).setOrigin(.5).setDepth(9)
          this.tweens.add({ targets: label, y: tank.y - (this.motionPreference.matches ? 50 : 95), alpha: 0, duration: 900, onComplete: () => label.destroy() })
        }
        if (!tank.alive&&!this.motionPreference.matches) {
          const smoke = this.add.image(tank.x, tank.y, 'tank-smoke').setDepth(8).setScale(.45)
          this.tweens.add({ targets: smoke, scale: 1.3, alpha: 0, duration: 600, onComplete: () => smoke.destroy() })
        }
      }
      if(previous!==undefined&&tank.health>previous&&this.lastState?.tanks[id]?.alive&&tank.alive)this.effects.emit(tank.x,tank.y,0x84e6a5,35)
      this.previousHealth.set(id, tank.health)
    }
    this.terrain?.setTint(tankMap(state.mapId).terrain)
    if (this.ground) this.buildBattlefield(state)
    this.lastState = state
    this.cameras.main.setBounds(0, 0, state.mapWidth, state.mapHeight)
  }

  getAim(): { targetX: number; targetY: number; shooting: boolean } | null {
    if (!this.cameraReady || this.lastState?.mode !== 'ASSAULT') return null
    const tank = this.lastState.tanks[this.playerId]
    if (!tank) return null
    const point = this.touchAim ?? (this.mouseAim
      ? this.cameras.main.getWorldPoint(this.mouseAim.x, this.mouseAim.y)
      : { x: tank.x + Math.cos(tank.turretAngle) * 500, y: tank.y + Math.sin(tank.turretAngle) * 500 })
    return { targetX: point.x, targetY: point.y, shooting: this.mouseShooting }
  }

  releasePointer() {
    this.mouseShooting = false
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
    this.drawAssault(this.lastState)
    this.effects.draw(this.graphics,delta)
  }

  private drawBackground() {
    const view = this.cameras.main.worldView
    this.terrain.setPosition(view.x, view.y).setSize(view.width + 2, view.height + 2)
    this.terrain.tilePositionX = view.x
    this.terrain.tilePositionY = view.y
  }

  private buildBattlefield(state: TankGameState) {
    const key = `${state.mapId}-${state.mapWidth}-${state.mapHeight}-${state.mode}`
    if (key === this.battlefieldKey) return
    this.battlefieldKey = key
    const g = this.ground.clear(), w = state.mapWidth, h = state.mapHeight
    if (state.mode !== 'ASSAULT') return
    const road = state.mapId === 'CANYON' ? 0xd4b081 : ['WAREHOUSE', 'CROSSROADS'].includes(state.mapId) ? 0x39453f : 0xa69b75
    // Broad roads and worn shoulders explain navigable corridors without adding collision.
    const paths = state.mapId === 'CANYON'
      ? [[.08,.83,.17,.82,.17,.72,.35,.72,.35,.27,.52,.27,.52,.77,.70,.77,.70,.22,.90,.22]]
      : [[.04,.5,.96,.5],[.5,.04,.5,.96]]
    for (const path of paths) {
      for (const [thickness, alpha] of [[88,.18],[62,.48]] as const) {
        g.lineStyle(thickness, road, alpha).beginPath().moveTo(path[0]*w,path[1]*h)
        for(let i=2;i<path.length;i+=2)g.lineTo(path[i]*w,path[i+1]*h)
        g.strokePath()
      }
    }
    if (state.mapId === 'CROSSROADS') for (let n=1;n<20;n++) {
      g.fillStyle(0xd3c99b,.35).fillRect(w*n/20-10,h*.5-2,20,4).fillRect(w*.5-2,h*n/20-10,4,20)
    }
    const seed = state.mapId.split('').reduce((n,c)=>n+c.charCodeAt(0),0)
    for(let n=0;n<65;n++) {
      const x=45+((n*197+seed*31)%Math.max(1,w-90)),y=45+((n*317+seed*53)%Math.max(1,h-90))
      if(state.walls.some(wall=>x>wall.x-15&&x<wall.x+wall.width+15&&y>wall.y-15&&y<wall.y+wall.height+15))continue
      if(n%4===0){g.fillStyle(0x283b2b,.17).fillEllipse(x,y,25+n%17,15+n%11);g.lineStyle(1,0xc2c1a0,.12).strokeEllipse(x,y,30+n%17,20+n%11)}
      else {g.lineStyle(2,0x243326,.16).lineBetween(x,y,x+20,y+14);g.lineBetween(x+8,y-9,x+28,y+5)}
    }
    if (['WAREHOUSE','DEPOT'].includes(state.mapId)) for(let n=1;n<14;n++) {
      g.lineStyle(2,0xd5c291,.18).lineBetween(w*.44,h*n/14,w*.56,h*n/14)
    }
  }

  private drawWalls(state: TankGameState) {
    const view = Phaser.Geom.Rectangle.Inflate(Phaser.Geom.Rectangle.Clone(this.cameras.main.worldView), 80, 80)
    for (const wall of state.walls) {
      if (!Phaser.Geom.Rectangle.Overlaps(view, new Phaser.Geom.Rectangle(wall.x, wall.y, wall.width, wall.height))) continue
      this.graphics.fillStyle(0x050811, .35)
      this.graphics.fillRoundedRect(wall.x + 5, wall.y + 7, wall.width, wall.height, 5)
      this.graphics.fillStyle(tankMap(state.mapId).wall, 1)
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
        .setRotation(state.mode === 'ASSAULT' ? Phaser.Math.Angle.Wrap(tank.turretAngle - rendered.angle) : 0)
      if (!tank.alive || !sprite.visible) continue
      this.graphics.fillStyle(0x101a16, .9).fillRoundedRect(rendered.x - 21, rendered.y - 34, 42, 6, 3)
      this.graphics.fillStyle(tank.health / (tank.maxHealth || 100) > .3 ? 0x84e6a5 : 0xff8071)
        .fillRoundedRect(rendered.x - 20, rendered.y - 33, 40 * Math.max(0, tank.health) / (tank.maxHealth || 100), 4, 2)
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

  private drawAssault(state: TankGameState) {
    this.objectiveLabel?.setVisible(false)
    if (state.mode !== 'ASSAULT') return
    const g = this.graphics
    const mission = state.mission
    if (mission) {
      const color = mission.secured ? 0x9cddad : mission.status === 'CONTESTED' ? 0xff9e78 : 0xe4c67a
      const pulse = this.motionPreference.matches ? 1 : .85 + Math.sin(this.time.now / 450) * .15
      g.fillStyle(color,.06).fillCircle(mission.x,mission.y,mission.radius)
      g.lineStyle(2,color,.75*pulse).strokeCircle(mission.x,mission.y,mission.radius)
      g.lineStyle(5,color,.85).beginPath().arc(mission.x,mission.y,mission.radius-7,-Math.PI/2,-Math.PI/2+Math.PI*2*Math.min(1,mission.heldMs/mission.requiredMs),false).strokePath()
      g.fillStyle(0x182a21,.9).fillRoundedRect(mission.x-18,mission.y-20,36,40,4)
      g.lineStyle(2,color).lineBetween(mission.x-8,mission.y+12,mission.x-8,mission.y-12)
      g.fillStyle(color).fillTriangle(mission.x-7,mission.y-12,mission.x+12,mission.y-6,mission.x-7,mission.y)
      const name = tankOperation(state.mapId).sectors[mission.sector-1] ?? 'Mục tiêu'
      this.objectiveLabel.setText(`${mission.secured?'✓':mission.sector}  ${name}`).setPosition(mission.x,mission.y-mission.radius-25).setVisible(true)
      const me=state.tanks[this.playerId],view=this.cameras.main.worldView
      if(me?.alive&&!view.contains(mission.x,mission.y)) {
        const angle=Math.atan2(mission.y-me.y,mission.x-me.x),distance=Math.min(view.width,view.height)*.32
        const x=me.x+Math.cos(angle)*distance,y=me.y+Math.sin(angle)*distance
        const tip=Phaser.Math.RotateAround(new Phaser.Math.Vector2(x+12,y),x,y,angle)
        const left=Phaser.Math.RotateAround(new Phaser.Math.Vector2(x-7,y-8),x,y,angle),right=Phaser.Math.RotateAround(new Phaser.Math.Vector2(x-7,y+8),x,y,angle)
        g.fillStyle(color,.9).fillTriangle(tip.x,tip.y,left.x,left.y,right.x,right.y)
      }
    }
    const elapsed=this.time.now-this.snapshotAt
    for(const warning of state.warnings??[]) {
      const remaining=Math.max(0,warning.remainingMs-elapsed),progress=1-remaining/1400
      g.fillStyle(0xff8059,.1+progress*.16).fillCircle(warning.x,warning.y,warning.radius)
      g.lineStyle(2,0xffb174,.9).strokeCircle(warning.x,warning.y,warning.radius)
      g.lineStyle(3,0xffe6a2,.9).strokeCircle(warning.x,warning.y,Math.max(4,warning.radius*(1-progress)))
      g.lineStyle(2,0xffc18e,.8).lineBetween(warning.x-9,warning.y-9,warning.x+9,warning.y+9).lineBetween(warning.x+9,warning.y-9,warning.x-9,warning.y+9)
    }
    for (const unit of state.infantry ?? []) {
      const target = Object.values(state.tanks).filter(t => t.alive && !t.playerId.startsWith('bot-'))
        .sort((a, b) => Phaser.Math.Distance.Between(a.x, a.y, unit.x, unit.y) - Phaser.Math.Distance.Between(b.x, b.y, unit.x, unit.y))[0]
      const angle = unit.aimAngle ?? (target ? Math.atan2(target.y - unit.y, target.x - unit.x) : 0)
      const stride = this.motionPreference.matches ? 0 : Math.sin(this.time.now / 100 + unit.x) * 3
      g.fillStyle(0x090e0c, .35).fillEllipse(unit.x + 3, unit.y + 5, 19, 13)
      g.lineStyle(4, 0x29362c).lineBetween(unit.x - 4, unit.y + 5, unit.x - 4 + stride, unit.y + 10)
      g.lineBetween(unit.x + 4, unit.y + 5, unit.x + 4 - stride, unit.y + 10)
      g.fillStyle(unit.kind==='GRENADIER'?0xd29b48:0xa66545).fillRoundedRect(unit.x - 7, unit.y - 4, 14, 13, 3)
      g.fillStyle(0xe8c29a).fillCircle(unit.x, unit.y - 5, 5)
      g.fillStyle(0x586746).fillEllipse(unit.x, unit.y - 8, 13, 7)
      g.lineStyle(3, 0x222c29).lineBetween(unit.x, unit.y, unit.x + Math.cos(angle) * 17, unit.y + Math.sin(angle) * 17)
      if(unit.kind==='GRENADIER')g.lineStyle(2,0xffd588).strokeCircle(unit.x,unit.y-22,4)
      if (unit.kind !== 'GRENADIER' && unit.cooldown >= 33 && target && Phaser.Math.Distance.Between(unit.x, unit.y, target.x, target.y) < 310)
        g.lineStyle(1, 0xffcf72, .5).lineBetween(unit.x, unit.y, target.x, target.y)
      g.fillStyle(0xef8063).fillRect(unit.x - 9, unit.y - 17, 18 * Math.min(1, unit.health / 60), 3)
    }
    const now = (state.serverTime ?? Date.now()) + this.time.now - this.snapshotAt
    for (const blast of state.explosions ?? []) {
      const life = Phaser.Math.Clamp((blast.expiresAt - now) / 650, 0, 1)
      if (life <= 0) continue
      const radius = blast.radius * (1 - life * .6)
      g.fillStyle(0xff8f38, life * .24).fillCircle(blast.x, blast.y, radius)
      g.lineStyle(3, 0xffcd75, life).strokeCircle(blast.x, blast.y, radius)
      g.fillStyle(0xffecc1, life * .75).fillCircle(blast.x, blast.y, 25 * life)
    }
    for (const supply of state.supplies ?? []) {
      const color = { SHELLS: 0xe2c074, FUEL: 0x7ab5bd, REPAIR: 0xb6bf9b }[supply.kind]
      g.fillStyle(0x1d2720, supply.quantity > 0 ? .95 : .35).fillRoundedRect(supply.x - 16, supply.y - 16, 32, 32, 4)
      g.lineStyle(2, color, supply.quantity > 0 ? 1 : .25).strokeRoundedRect(supply.x - 16, supply.y - 16, 32, 32, 4)
      g.lineBetween(supply.x - 8, supply.y, supply.x + 8, supply.y)
      if (supply.kind === 'REPAIR') g.lineBetween(supply.x, supply.y - 8, supply.x, supply.y + 8)
      else if (supply.kind === 'SHELLS') g.strokeRect(supply.x - 5, supply.y - 9, 10, 18)
      else g.strokeCircle(supply.x, supply.y, 8)
    }
    for (const contact of state.contacts ?? []) if (!contact.current) {
      g.lineStyle(1, 0xccb68a, .4).strokeCircle(contact.x, contact.y, 15)
      g.lineBetween(contact.x - 5, contact.y, contact.x + 5, contact.y)
    }
    const tank = state.tanks[this.playerId]
    const aim = this.getAim()
    if (!tank?.alive || !aim) return
    const range = tank.tacticalView?.range ?? 700
    g.lineStyle(1, 0xc2cbb0, .16).strokeCircle(tank.x, tank.y, range)
    const distance = Math.min(range, Phaser.Math.Distance.Between(tank.x, tank.y, aim.targetX, aim.targetY))
    const angle = Math.atan2(aim.targetY - tank.y, aim.targetX - tank.x)
    const x = tank.x + Math.cos(angle) * distance, y = tank.y + Math.sin(angle) * distance
    const impact=shellImpact(tank.x,tank.y,x,y,state.walls)
    const danger=Phaser.Math.Distance.Between(tank.x,tank.y,impact.x,impact.y)<120
    const color=danger?0xff9b75:impact.blocked?0xe7c16f:0xd8d9b6
    g.lineStyle(1,color,.32).lineBetween(tank.x,tank.y,impact.x,impact.y)
    g.lineStyle(1,color,.28).strokeCircle(impact.x,impact.y,tank.tacticalView?.weapon.state==='READY'?120:18)
    g.lineStyle(2,color,.9).lineBetween(impact.x-7,impact.y,impact.x+7,impact.y).lineBetween(impact.x,impact.y-7,impact.x,impact.y+7)
    const barrelX=tank.x+Math.cos(tank.turretAngle)*65,barrelY=tank.y+Math.sin(tank.turretAngle)*65
    g.lineStyle(3,0xe4c67a,.65).lineBetween(tank.x+Math.cos(tank.turretAngle)*35,tank.y+Math.sin(tank.turretAngle)*35,barrelX,barrelY)
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
