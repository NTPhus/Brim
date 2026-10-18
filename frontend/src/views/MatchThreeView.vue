<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useBoardSession } from '@/composables/useBoardSession'
import type { MatchThreeState } from '@/types/arcade'
import '@/game/arcade.css'
import { useGameMotion } from '@/composables/useGameMotion'
import { animatePuzzleFrame } from '@/game/puzzleMotion'
const game=useBoardSession<MatchThreeState>('MATCH_THREE')
const { state, error, pending, connected, finished }=game
const motionBoard=ref<HTMLElement|null>(null)
const motion=useGameMotion()
const shapes=['','◆','●','▲','■','★','♥']
const selected=ref<{row:number;column:number}|null>(null)
const display=ref<number[][]|null>(null)
const animating=ref(false)
const announcement=ref('')
let generation=0
const board=computed(()=>display.value??state.value?.board)
let fingerprint=''
watch(state,async(next,previous)=>{
  if(!next)return
  const token=JSON.stringify([next.level,next.movesLeft,next.score,next.board,next.levelStatus])
  if(token===fingerprint)return
  fingerprint=token
  const before=board.value?.map(row=>[...row])??next.board
  generation++;motion.cancel();selected.value=null;animating.value=false
  const current=generation
  if(!previous||next.level!==previous.level||next.movesLeft>previous.movesLeft||motion.reduced()||!next.events.length){display.value=next.board;return}
  animating.value=true
  let last=before
  for(const event of next.events){
    if(generation!==current)return
    announcement.value=event.type==='CLEAR'?`+${event.points} điểm`:event.type==='RESHUFFLE'?'Đã tạo bàn mới để có nước đi':''
    await animatePuzzleFrame(motionBoard.value,last,event,motion,()=>{display.value=event.board},()=>generation===current)
    last=event.board
  }
  if(generation===current){display.value=next.board;animating.value=false}
})
const preference=window.matchMedia('(prefers-reduced-motion: reduce)')
function reduceMotion(){if(preference.matches){generation++;motion.cancel();display.value=state.value?.board??null;animating.value=false}}
preference.addEventListener('change',reduceMotion)
onUnmounted(()=>{generation++;preference.removeEventListener('change',reduceMotion)})
function choose(row:number,column:number) {
  if (!state.value||state.value.levelStatus!=='PLAYING'||pending.value||animating.value) return
  const previous=selected.value
  if (!previous) { selected.value={row,column};return }
  if (previous.row===row&&previous.column===column) {selected.value=null;return}
  if (Math.abs(previous.row-row)+Math.abs(previous.column-column)!==1) {selected.value={row,column};return}
  const cells=state.value.board.map(line=>[...line]);
  [cells[row][column],cells[previous.row][previous.column]]=[cells[previous.row][previous.column],cells[row][column]]
  let match=false
  for (let r=0;r<8;r++) for (let c=0;c<8;c++) {
    const v=cells[r][c];if(v<=0)continue
    if ((c<6&&cells[r][c+1]===v&&cells[r][c+2]===v)||(r<6&&cells[r+1][c]===v&&cells[r+2][c]===v))match=true
  }
  selected.value=null
  if (!match) {error.value='Đổi hai ô phải tạo ít nhất một bộ ba. Bạn không bị trừ lượt.';return}
  game.move({action:'SWAP',row:previous.row,column:previous.column,toRow:row,toColumn:column})
}
</script>
<template>
  <section class="arcade card match-three" :class="{ 'game-finished':finished }">
    <header><h1>💎 Match-3</h1><button class="secondary" @click="game.leave">Rời bàn</button></header>
    <p role="status" aria-live="polite">{{ !connected?'Đang kết nối lại…':finished?'Bạn đã hoàn thành cả 10 màn! 🏆':state?.levelStatus==='WON'?'Hoàn thành màn!':state?.levelStatus==='LOST'?'Hết lượt. Hãy thử lại!':'Chọn hai ô kề nhau để đổi chỗ.' }}</p>
    <template v-if="state">
      <div class="levels" aria-label="Chọn màn">
        <button v-for="level in 10" :key="level" :class="{ current:level===state.level }" :disabled="level>state.unlocked||pending||animating||finished||!connected" @click="game.move({action:'LEVEL',level})">{{ level>state.unlocked?'🔒':level }}</button>
      </div>
      <div class="arcade-stats"><strong>Màn {{ state.level }}/10</strong><span>Điểm {{ state.score }}/{{ state.target }}</span><span>{{ state.movesLeft }} lượt</span>
        <span v-if="state.collectTarget">Thu thập {{ shapes[state.collectType] }}: {{ state.collected }}/{{ state.collectTarget }}</span>
        <span v-if="state.covers.some(row=>row.some(Boolean))">Phá hết ô có viền vàng</span>
      </div>
      <div v-if="board" ref="motionBoard" class="arcade-grid gems" :aria-busy="animating">
        <template v-for="(row,r) in board" :key="r"><button v-for="(value,c) in row" :key="c" class="grid-cell" :class="[`gem-${value}`,{ selected:selected?.row===r&&selected?.column===c, covered:state.covers[r][c]>0 }]"
          :disabled="!connected||pending||animating||state.levelStatus!=='PLAYING'||value<=0" :aria-label="`Hàng ${r+1}, cột ${c+1}, ${value===-1?'vật cản':shapes[value]}`" :aria-pressed="selected?.row===r&&selected?.column===c" @click="choose(r,c)"><span class="motion-piece">{{ value===-1?'▧':shapes[value] }}</span></button></template>
      </div>
      <p class="animation-status" aria-live="polite">{{ announcement }}</p>
      <div class="arcade-actions">
        <button v-if="state.levelStatus==='WON'&&!finished" :disabled="pending||animating||!connected" @click="game.move({action:'NEXT'})">Màn tiếp theo →</button>
        <button v-if="!finished" class="secondary" :disabled="pending||animating||!connected" @click="game.move({action:'RETRY'})">Chơi lại màn</button>
        <button v-if="finished" :disabled="!connected" @click="game.restart">Chơi lại</button>
      </div>
    </template>
    <p class="arcade-help">Ghép từ 3 hình cùng loại theo hàng ngang/dọc. Combo liên hoàn tăng điểm. Ô ▧ chặn trọng lực; ghép hình trên lớp phủ viền vàng để phá lớp phủ. Màn đã mở được lưu theo phiên người chơi trên server; tải lại sẽ giữ trận đang chơi.</p>
    <p v-if="error" class="arcade-error" role="alert">{{ error }}</p>
  </section>
</template>
<style scoped>
.match-three { max-width:640px; }
.levels { display:grid;grid-template-columns:repeat(10,minmax(0,1fr));gap:4px; }
.levels button { padding:.5rem 0;font-size:.85rem; }
.levels .current { outline:2px solid #ffe17a; }
.gems { grid-template-columns:repeat(8,minmax(0,1fr)); }
.gems .grid-cell { font-size:clamp(1.1rem,5vw,2.2rem); }
.gems .gem-1 { color:#fda4af; }.gems .gem-2 { color:#7dd3fc; }.gems .gem-3 { color:#c4b5fd; }
.gems .gem-4 { color:#6ee7b7; }.gems .gem-5 { color:#fde68a; }.gems .gem-6 { color:#f9a8d4; }
.gems .gem--1 { background:#4b5563;color:#cbd5e1; }
.gems .covered { box-shadow:inset 0 0 0 2px #e9b949; }
.gems .selected { outline:3px solid white;outline-offset:-3px;background:#46546b; }
.animation-status { min-height:1.5em;color:#fde68a; }
</style>
