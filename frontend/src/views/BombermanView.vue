<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useBoardSession } from '@/composables/useBoardSession'
import type { BombermanState } from '@/types/arcade'
import '@/game/arcade.css'
import { useBoardMotion } from '@/composables/useBoardMotion'
const game=useBoardSession<BombermanState>('BOMBERMAN')
const {state,room,connected,finished,result,error}=game
const me=computed(()=>state.value?.players.find(p=>p.id===game.session.player.id))
const keyMap:Record<string,string>={ArrowUp:'UP',ArrowDown:'DOWN',ArrowLeft:'LEFT',ArrowRight:'RIGHT',w:'UP',s:'DOWN',a:'LEFT',d:'RIGHT'}
const motionBoard=ref<HTMLElement|null>(null)
useBoardMotion(motionBoard,()=>state.value?{tokens:state.value.players.filter(p=>p.alive).map(p=>({key:p.id,kind:p.id,position:p.row+','+p.column,effect:'place' as const}))}:null)
const held=new Set<string>()
const fire=()=>{if(me.value?.alive&&!finished.value)game.move({action:'BOMB'})}
let directionHeld='STOP'
let repeatTimer:number|undefined
function move(direction:string) { directionHeld=direction; if(me.value?.alive&&!finished.value&&connected.value)game.move({action:'MOVE',direction}) }
watch(connected, value=>{if(!value){held.clear();directionHeld='STOP'}})
function stop() {held.clear();move('STOP')}
function down(event:KeyboardEvent) {
  if ((event.target as HTMLElement)?.closest('input,textarea,select,button')) return
  const key=event.key.length===1?event.key.toLowerCase():event.key
  if (key===' ') {event.preventDefault();if(!event.repeat)fire();return}
  if (keyMap[key]) {event.preventDefault();if(!event.repeat){held.delete(key);held.add(key);move(keyMap[key])}}
}
function up(event:KeyboardEvent) {
  const key=event.key.length===1?event.key.toLowerCase():event.key
  if (!held.has(key))return
  event.preventDefault();held.delete(key)
  const last=[...held].at(-1);move(last?keyMap[last]:'STOP')
}
const bombs=computed(()=>new Set(state.value?.bombs.map(b=>`${b.row},${b.column}`)))
const flames=computed(()=>new Set(state.value?.flames.map(b=>`${b.row},${b.column}`)))
function icon(row:number,column:number,value:number) {
  const key=`${row},${column}`
  if (flames.value.has(key))return '🔥'
  if (bombs.value.has(key))return '💣'
  if (state.value?.items[key])return state.value.items[key]==='RANGE'?'↔':'＋'
  return value===1?'▧':value===2?'▤':''
}
function visibility() {if(document.hidden)stop()}
onMounted(()=>{repeatTimer=window.setInterval(()=>{if(directionHeld!=='STOP')move(directionHeld)},150);window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',stop);document.addEventListener('visibilitychange',visibility)})
onUnmounted(()=>{window.clearInterval(repeatTimer);stop();window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',stop);document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
  <section class="arcade card bomberman" :class="{ 'game-finished':finished }">
    <header><h1>💣 Bomberman</h1><button class="secondary" @click="game.leave">Rời bàn</button></header>
    <p role="status" aria-live="polite">{{ !connected?'Đang kết nối lại…':finished?result:me?.alive?'Phá đường, đặt bom và thoát khỏi vùng nổ!':'Bạn đã bị loại. Đang theo dõi trận đấu.' }}</p>
    <template v-if="state">
      <div class="arcade-stats"><span>⏱ {{ Math.ceil(state.ticksLeft/10) }}s</span><span>Bạn: số {{ state.players.findIndex(p=>p.id===game.session.player.id)+1 }}</span><span>Tầm nổ: {{ me?.range }} · Số bom: {{ me?.capacity }}</span></div>
      <div ref="motionBoard" class="arcade-grid arena" aria-label="Đấu trường Bomberman">
        <template v-for="(row,r) in state.board" :key="r"><div v-for="(value,c) in row" :key="c" class="grid-cell"
          :class="{ wall:value===1, crate:value===2, flame:flames.has(`${r},${c}`),  }"><span :class="{ 'bomb-fuse':bombs.has(`${r},${c}`)&&!flames.has(`${r},${c}`), 'blast-burst':flames.has(`${r},${c}`) }">{{ icon(r,c,value) }}</span></div></template>
        <div class="bomber-actors" aria-hidden="true"><span v-for="player in state.players.filter(p=>p.alive)" :key="player.id" class="bomber-actor" :class="{ self:player.id===game.session.player.id }" :data-motion-key="player.id" :style="{left:(player.column/state.board[0].length*100)+'%',top:(player.row/state.board.length*100)+'%',width:(100/state.board[0].length)+'%',height:(100/state.board.length)+'%'}">{{ state.players.findIndex(p=>p.id===player.id)+1 }}</span></div>
      </div>
      <div class="arcade-players"><span v-for="(player,index) in state.players" :key="player.id">{{ index+1 }} · {{ player.name }} {{ player.alive?'':'☠' }}</span></div>
      <div v-if="me?.alive&&!finished" class="controls" aria-label="Điều khiển di động">
        <button v-for="[direction,label] in [['UP','↑'],['LEFT','←'],['DOWN','↓'],['RIGHT','→']]" :key="direction" :disabled="!connected"
          :aria-label="direction" @pointerdown.prevent="move(direction); ($event.target as HTMLElement).setPointerCapture($event.pointerId)" @pointerup="move('STOP')" @pointercancel="move('STOP')" @lostpointercapture="move('STOP')">{{ label }}</button>
        <button :disabled="!connected" @click="fire">💣 Đặt bom</button>
      </div>
    </template>
    <p class="arcade-help">Di chuyển: phím mũi tên/WASD, đặt bom: Space. Bom nổ sau 2 giây, có thể kích nổ bom khác; lửa tồn tại 0,5 giây. ▧ tường cố định · ▤ tường phá được · ↔ tăng tầm nổ · ＋ thêm bom. Người sống cuối thắng; hết 180 giây là hòa.</p>
    <p v-if="error" role="alert" class="arcade-error">{{ error }}</p>
    <button v-if="finished&&room?.hostId===game.session.player.id" :disabled="!connected" @click="game.restart">Chơi ván mới</button>
  </section>
</template>
<style scoped>
.arena {grid-template-columns:repeat(13,minmax(0,1fr));gap:2px;background:#152b26;}
.arena .grid-cell {background:#254c39;font-size:clamp(.65rem,3vw,1.7rem);border-radius:2px;}
.arena .wall {background:#3f4d62;color:#91a2b8;}.arena .crate {background:#795239;color:#d8aa7d;}
.arena .avatar {color:white;background:#7c3da6;font-weight:800;}.arena .self {outline:3px solid #ffe17a;outline-offset:-3px;}
.arena .flame {background:#a34625;}.controls {display:flex;gap:.5rem;flex-wrap:wrap;margin:1rem 0;}
.controls button {min-width:48px;min-height:48px;touch-action:none;user-select:none;}

.arena {position:relative;}
.bomber-actors {position:absolute;inset:4px;pointer-events:none;}
.bomber-actor {position:absolute;display:grid;place-items:center;z-index:3;font-size:clamp(.7rem,3vw,1.5rem);font-weight:900;color:#fff;text-shadow:0 1px 3px #000;background:radial-gradient(circle,#a465db 0 50%,transparent 53%);}
.bomber-actor.self {background:radial-gradient(circle,#9a62cb 0 43%,#ffe17a 45% 52%,transparent 55%);outline:none;}
.bomb-fuse {display:inline-block;animation:bomb-fuse 600ms ease-in-out infinite alternate;}
.blast-burst {display:inline-block;animation:blast-burst 400ms ease-out both;}
@keyframes bomb-fuse {from{transform:scale(.85) rotate(-5deg);}to{transform:scale(1.08) rotate(5deg);filter:brightness(1.2);}}
@keyframes blast-burst {from{transform:scale(.45);opacity:.5;}40%{transform:scale(1.2);opacity:1;}to{transform:scale(1);opacity:.8;}}
</style>
