<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoardSession } from '@/composables/useBoardSession'
import type { WarlordsState, WarlordsEvent, WarlordsFighter, WarlordsSkill } from '@/types/warlords'
import { resourceNames } from '@/types/warlords'
import WarlordTile from '@/components/warlords/WarlordTile.vue'
import WarlordFighter from '@/components/warlords/WarlordFighter.vue'
import WarlordsMap from '@/components/warlords/WarlordsMap.vue'
import WarlordsCamp from '@/components/warlords/WarlordsCamp.vue'
import WarlordsHeroes from '@/components/warlords/WarlordsHeroes.vue'
import '@/game/warlords.css'
import { useGameMotion } from '@/composables/useGameMotion'
import { animatePuzzleFrame } from '@/game/puzzleMotion'
const props=defineProps<{mode:'CAMPAIGN'|'DUEL'}>()
const route=useRoute()
const router=useRouter()
const game=useBoardSession<WarlordsState>(props.mode==='CAMPAIGN'?'WARLORDS_CAMPAIGN':'WARLORDS_DUEL')
const {state,room,error,pending,connected}=game
const selectedChapter=ref(Number(route.query.chapter)||1)
const selected=ref<{row:number;column:number}|null>(null)
const hint=ref<string[]>([])
const display=ref<number[][]|null>(null)
const animating=ref(false)
const syncing=ref(false)
const playingEvent=ref<WarlordsEvent|null>(null)
const movingCells=ref<string[]>([])
const lastAction=ref('')
const displayedFighters=ref<WarlordsFighter[]>([])
const log=ref<Array<{text:string;actor:string}>>([])
const shot=ref<{actor:string;damage:number;key:number}|null>(null)
const copyNotice=ref('')
const grid=ref<HTMLElement|null>(null)
const details=ref<HTMLDialogElement|null>(null)
const focusedCell=ref(0)
const confirmRetreat=ref(false)
const leaveAfterRetreat=ref(false)
let generation=0
const motion=useGameMotion()
const motionPreference=window.matchMedia('(prefers-reduced-motion: reduce)')
const now=ref(Date.now())
let clock:number|undefined
let frameTimer:number|undefined
let shotTimer:number|undefined
let previous=''
let encounter=''
let disposed=false
const queue:Array<{event:WarlordsEvent|null;board:number[][];fighters?:WarlordsFighter[]}>=[]
const fighters=computed(()=>displayedFighters.value.length?displayedFighters.value:state.value?.fighters??[])
const me=computed(()=>fighters.value.find(fighter=>fighter.id===game.session.player.id))
const selectedHero=computed(()=>state.value?.heroes.find(hero=>hero.id===state.value?.profile?.heroId))
const myTurn=computed(()=>state.value?.phase==='BATTLE'&&state.value.currentPlayer===game.session.player.id)
const canAct=computed(()=>myTurn.value&&connected.value&&!syncing.value&&!pending.value&&!animating.value&&seconds.value>0)
const chapter=computed(()=>state.value?.chapters.find(item=>item.id===selectedChapter.value))
const board=computed(()=>display.value??state.value?.board??[])
const seconds=computed(()=>Math.max(0,Math.ceil(((state.value?.turnDeadline??0)-now.value)/1000)))
const turnProgress=computed(()=>Math.min(100,seconds.value/45*100))
const attrition=computed(()=>state.value&&state.value.turnNumber>=40?4+Math.floor((state.value.turnNumber-40)/4):0)
const resourceGuide=computed(()=>[
  {value:1,effect:`${me.value?.attack??5} sát thương`},
  {value:2,effect:'+6 năng lượng'},
  {value:3,effect:'+3 HP'},
  {value:4,effect:'+4 khiên'},
  {value:5,effect:'+2 vàng'},
  {value:6,effect:'+3 XP'}
])
const inviteLink=computed(()=>`${window.location.origin}/room/${room.value?.id??''}`)
const won=computed(()=>state.value?.winner===game.session.player.id)
const reward=computed(()=>state.value?.rewards.find(item=>item.playerId===game.session.player.id))
const actingId=computed(()=>animating.value&&playingEvent.value?playingEvent.value.actor:state.value?.currentPlayer)
const opponentMoving=computed(()=>!!playingEvent.value&&playingEvent.value.actor!==game.session.player.id)
const actorName=(id:string)=>state.value?.fighters.find(f=>f.id===id)?.name??'Đối thủ'
const winnerName=computed(()=>state.value?.fighters.find(f=>f.id===state.value?.winner)?.name??'Đối thủ')
const statusText=computed(()=>{
  if(!connected.value)return 'Đang kết nối lại…'
  if(syncing.value)return 'Đang đồng bộ thế trận…'
  if(!state.value)return 'Đang chuẩn bị thế trận…'
  if(animating.value&&playingEvent.value){
    const event=playingEvent.value
    return `${actorName(event.actor)} · ${event.type==='SWAP'?'đang chọn và đổi hai ô':event.type==='CLEAR'?'đang ghép quân':event.type==='FALL'?'quân đang rơi':event.type==='SKILL'?event.text:'đang hoàn tất nước đi'}`
  }
  if(state.value.phase==='RESULT')return won.value?'Chiến thắng!':`${winnerName.value} đã thắng trận này.`
  if(state.value.phase==='MAP')return 'Chọn một chương đã mở để xuất quân.'
  if(seconds.value===0)return 'Hết thời gian lượt · đang chờ cập nhật thế trận…'
  return myTurn.value?'Đến lượt bạn — chọn hai ô kề nhau hoặc dùng kỹ năng.':`Đợi ${state.value.fighters.find(f=>f.id===state.value?.currentPlayer)?.name??'đối thủ'} ra quân…`
})
function send(action:string,extra:Record<string,unknown>={}){
  if(!state.value)return
  game.move({action,...extra,encounterId:state.value.encounterId,revision:state.value.revision})
}
function skillBlockReason(skill:WarlordsSkill){
  if(!connected.value)return 'Chờ kết nối lại'
  if(syncing.value)return 'Đang đồng bộ thế trận'
  if(state.value?.phase!=='BATTLE')return 'Trận đã kết thúc'
  if(animating.value)return 'Đang diễn nước đi'
  if(pending.value)return 'Chờ xác nhận'
  if(!myTurn.value)return 'Chờ lượt của bạn'
  if(seconds.value===0)return 'Đã hết thời gian lượt'
  if(skill.id==='HEAL'&&me.value?.hp===me.value?.maxHp)return 'HP đã đầy'
  if(skill.id==='GUARD'&&(me.value?.shield??0)>=80)return 'Khiên đã tối đa'
  const missing=skill.cost-(me.value?.energy??0)
  return missing>0?`Cần thêm ${missing} năng lượng`:''
}
function skillEffect(skill:WarlordsSkill){
  const rank=me.value?.skillRank??0
  return skill.id==='STRIKE'?`${28+rank*6} sát thương`:skill.id==='HEAL'?`Hồi ${30+rank*5} HP`:`Thêm ${40+rank*4} khiên`
}
function focusCell(index:number){
  focusedCell.value=index
  const tile=grid.value?.children[index] as HTMLButtonElement|undefined
  tile?.focus()
}
function requestRetreat(leave=false){
  confirmRetreat.value=true;leaveAfterRetreat.value=leave;details.value?.showModal()
}
function leaveRoom(){
  if(state.value?.phase==='BATTLE')requestRetreat(true)
  else void router.push('/warlords')
}
function retreat(){
  const leave=leaveAfterRetreat.value
  details.value?.close()
  if(leave)void router.push('/warlords')
  else send(props.mode==='CAMPAIGN'?'RETREAT':'RESIGN')
}
function navigateBoard(event:KeyboardEvent,row:number,column:number){
  const directions:Record<string,[number,number]>={ArrowUp:[-1,0],ArrowDown:[1,0],ArrowLeft:[0,-1],ArrowRight:[0,1]}
  const direction=directions[event.key]
  if(direction){
    event.preventDefault()
    const nextRow=Math.max(0,Math.min(7,row+direction[0]))
    const nextColumn=Math.max(0,Math.min(7,column+direction[1]))
    focusCell(nextRow*8+nextColumn)
  }else if(event.key==='Escape'){
    event.preventDefault();selected.value=null;hint.value=[]
  }else if(event.key==='Home'||event.key==='End'){
    event.preventDefault();focusCell((event.ctrlKey?(event.key==='Home'?0:7):row)*8+(event.key==='Home'?0:7))
  }
}
function matched(cells:number[][]){
  for(let r=0;r<8;r++)for(let c=0;c<8;c++){
    const value=cells[r][c]
    if(value>0&&((c<6&&cells[r][c+1]===value&&cells[r][c+2]===value)||(r<6&&cells[r+1][c]===value&&cells[r+2][c]===value)))return true
  }
  return false
}
function validSwap(r:number,c:number,y:number,x:number){
  if(!state.value)return false
  const cells=state.value.board.map(row=>[...row]);[cells[r][c],cells[y][x]]=[cells[y][x],cells[r][c]]
  return matched(cells)
}
function choose(row:number,column:number){
  if(!canAct.value)return
  hint.value=[]
  const old=selected.value
  focusedCell.value=row*8+column
  if(!old){selected.value={row,column};return}
  if(old.row===row&&old.column===column){selected.value=null;return}
  if(Math.abs(old.row-row)+Math.abs(old.column-column)!==1){selected.value={row,column};return}
  selected.value=null
  if(!validSwap(old.row,old.column,row,column)){error.value='Nước đổi chưa tạo được bộ ba. Bạn không mất lượt.';return}
  send('SWAP',{row:old.row,column:old.column,toRow:row,toColumn:column})
}
function showHint(){
  if(!canAct.value)return
  for(let r=0;r<8;r++)for(let c=0;c<8;c++)for(const [dr,dc] of [[0,1],[1,0]]){
    const y=r+dr,x=c+dc
    if(y<8&&x<8&&validSwap(r,c,y,x)){hint.value=[`${r},${c}`,`${y},${x}`];selected.value=null;focusCell(r*8+c);return}
  }
}
async function playFrames(){
  if(disposed)return
  const frame=queue.shift()
  if(!frame){animating.value=false;playingEvent.value=null;movingCells.value=[];display.value=state.value?.board??null;return}
  const version=generation
  animating.value=true
  const before=board.value.map(row=>[...row])
  const type=frame.event?.type
  if(frame.event)playingEvent.value=frame.event
  movingCells.value=type==='SWAP'?(frame.event?.cells??[]).map(cell=>`${cell.row},${cell.column}`):[]
  if(type==='SWAP'){
    await nextTick()
    await Promise.all((frame.event?.cells??[]).map(cell=>motion.animate(grid.value?.children[cell.row*8+cell.column],
      [{filter:'brightness(1)'},{filter:'brightness(1.7)'},{filter:'brightness(1)'}],420)))
    if(disposed||version!==generation)return
  }
  await animatePuzzleFrame(grid.value,before,{type:type??'',cells:frame.event?.cells??[],board:frame.board},motion,()=>{display.value=frame.board},()=>!disposed&&version===generation)
  if(disposed||version!==generation)return
  movingCells.value=[]
  const effect=frame.event?.effects
  if(effect&&frame.event){
    displayedFighters.value=fighters.value.map(fighter=>fighter.id===frame.event!.actor
      ?{...fighter,hp:Math.min(fighter.maxHp,fighter.hp+effect.healing),energy:Math.max(0,Math.min(100,fighter.energy+effect.energy)),shield:Math.min(80,fighter.shield+effect.shield),gold:fighter.gold+effect.gold,experience:fighter.experience+effect.experience}
      :{...fighter,hp:Math.max(0,fighter.hp-effect.damage),shield:Math.max(0,fighter.shield-effect.absorbed)})
  }
  if(frame.fighters)displayedFighters.value=frame.fighters
  if(effect&&(effect.damage>0||effect.absorbed>0)){
    window.clearTimeout(shotTimer)
    shot.value={actor:frame.event!.actor,damage:effect.damage,key:Date.now()}
    shotTimer=window.setTimeout(()=>{shot.value=null},420)
  }
  frameTimer=window.setTimeout(()=>void playFrames(),type==='SKILL'?480:type==='SWAP'?140:65)
}
watch(state,next=>{
  if(!next)return
  syncing.value=false
  if(next.profile&&selectedChapter.value>next.profile.unlocked)selectedChapter.value=next.profile.unlocked
  const token=`${next.encounterId}:${next.revision}`
  if(token===previous)return
  previous=token;selected.value=null;hint.value=[]
  const freshEncounter=encounter!==next.encounterId
  if(freshEncounter||next.phase==='MAP'){
    generation++;motion.cancel();encounter=next.encounterId;queue.length=0;window.clearTimeout(frameTimer);animating.value=false;display.value=next.board
    playingEvent.value=null;movingCells.value=[];lastAction.value=''
    displayedFighters.value=next.fighters
    if(next.phase!=='MAP')log.value=[]
  }
  const action=next.events.find(event=>event.type==='SWAP'||event.type==='SKILL')
  if(action)lastAction.value=action.type==='SWAP'
    ?`${actorName(action.actor)} đổi ô (${action.cells[0].row+1}, ${action.cells[0].column+1}) ↔ (${action.cells[1].row+1}, ${action.cells[1].column+1})`
    :`${actorName(action.actor)} · ${action.text}`
  for(const event of next.events)if(event.text)log.value.unshift({actor:next.fighters.find(f=>f.id===event.actor)?.name??'',text:event.text})
  log.value=log.value.slice(0,18)
  if(next.phase==='MAP'){display.value=null;return}
  if(freshEncounter||motionPreference.matches){
    generation++;motion.cancel();window.clearTimeout(frameTimer)
    queue.length=0;animating.value=false;display.value=next.board;displayedFighters.value=next.fighters;playingEvent.value=null;movingCells.value=[];return
  }
  for(const event of next.events)queue.push({event,board:event.board})
  queue.push({event:null,board:next.board,fighters:next.fighters})
  if(queue.length>100){
    generation++;motion.cancel();window.clearTimeout(frameTimer)
    queue.length=0;animating.value=false;display.value=next.board
    playingEvent.value=null;movingCells.value=[]
    displayedFighters.value=next.fighters
  }
  if(!animating.value)playFrames()
},{flush:'sync'})
watch(connected,isConnected=>{
  if(isConnected)return
  selected.value=null;hint.value=[]
  encounter='';previous='';syncing.value=true
  generation++;motion.cancel();window.clearTimeout(frameTimer);queue.length=0
  window.clearTimeout(shotTimer);shot.value=null
  animating.value=false;playingEvent.value=null;movingCells.value=[]
  display.value=state.value?.board??null;displayedFighters.value=state.value?.fighters??[]
})
async function copyInvite(){
  try{await navigator.clipboard.writeText(inviteLink.value);copyNotice.value='Đã sao chép link phòng.'}
  catch{copyNotice.value='Bạn có thể chọn và sao chép đường dẫn bên dưới.'}
}
function updateMotionPreference(){
  if(!motionPreference.matches)return
  generation++;motion.cancel();window.clearTimeout(frameTimer)
  queue.length=0;animating.value=false;display.value=state.value?.board??null
  playingEvent.value=null;movingCells.value=[]
  displayedFighters.value=state.value?.fighters??[]
}
onMounted(()=>{clock=window.setInterval(()=>{now.value=Date.now()},500);motionPreference.addEventListener('change',updateMotionPreference)})
onUnmounted(()=>{disposed=true;generation++;motion.cancel();queue.length=0;window.clearInterval(clock);window.clearTimeout(frameTimer);window.clearTimeout(shotTimer);motionPreference.removeEventListener('change',updateMotionPreference)})
</script>
<template>
  <div class="warlords warlords-battle" :class="{ 'in-combat':state?.phase!=='MAP' }">
    <header class="war-header"><div><span class="war-eyebrow">SƠN HÀ · {{ mode==='CAMPAIGN'?'HỢP CỜ HOA LƯ':'ĐẤU TƯỚNG TRỰC TUYẾN' }}</span><h1>{{ state?.phase==='MAP'?'Bản đồ sơn hà':mode==='DUEL'?'Đấu tướng':state?.chapter?.title??'Chuẩn bị xuất quân' }}</h1><span class="war-muted room-title">{{ room?.name }}</span></div><div class="war-header-actions"><button v-if="state?.phase!=='MAP'" class="war-button secondary small" :disabled="!state" @click="details?.showModal()">Hướng dẫn / Nhật ký</button><button v-if="mode==='DUEL'" class="war-button secondary small" @click="copyInvite">Link phòng</button><button class="war-button secondary small" @click="leaveRoom">Rời phòng</button></div></header>
    <div v-if="copyNotice" class="invite-copy"><p role="status">{{ copyNotice }}</p><button class="war-button secondary small" @click="copyNotice=''">Đóng ×</button><input :value="inviteLink" readonly aria-label="Đường dẫn mời vào phòng" @focus="($event.target as HTMLInputElement).select()"/></div>
    <button v-if="error" class="war-error battle-error" role="alert" @click="error=''">{{ error }} · Đóng ×</button>
    <div class="battle-status" :class="{ 'your-turn':myTurn&&connected, reconnecting:!connected }">
      <span class="status-dot" :class="{ live:myTurn&&connected }" aria-hidden="true"/>
      <span class="turn-message" role="status" aria-live="polite">{{ statusText }}</span>
      <div v-if="state?.phase==='BATTLE'&&connected" class="turn-clock" :class="{ urgent:seconds<=10 }" role="timer" :aria-label="`Còn ${seconds} giây trong lượt`">
        <strong>{{ seconds }}<small> / 45s</small></strong><span class="turn-clock-track" aria-hidden="true"><span :style="{width:`${turnProgress}%`}"/></span>
      </div>
    </div>
    <template v-if="state">
      <div v-if="state.phase==='MAP'&&state.profile" class="war-campaign-layout">
        <WarlordsMap :chapters="state.chapters" :profile="state.profile" :selected="selectedChapter" :disabled="pending||!connected" @select="selectedChapter=$event"/>
        <aside class="war-sidebar"><section v-if="chapter" class="war-panel chapter-detail"><span class="war-eyebrow">CHƯƠNG {{ chapter.id }} · {{ chapter.encounter }}</span><h2>{{ chapter.title }}</h2><strong>{{ chapter.warlord }} · {{ chapter.location }}</strong><p>{{ chapter.story }}</p><div class="chapter-enemy"><span>{{ chapter.enemyHp }} HP</span><span>{{ chapter.enemyAttack }} công/kiếm</span></div><p class="war-muted">AI {{ chapter.botLevel }}/4 · {{ chapter.botStyle }}</p><div class="chapter-rewards"><span>◈ {{ chapter.gold }} vàng</span><span>✦ {{ chapter.experience }} XP</span></div><p v-if="!state.profile.cleared.includes(chapter.id)" class="war-muted">Lần đầu qua chương: thêm 50 vàng và 30 XP.</p><button class="war-button" :disabled="pending||!connected" @click="send('START',{chapter:chapter.id})">Xuất quân →</button></section><WarlordsHeroes :heroes="state.heroes" :selected="state.profile.heroId" :busy="pending||!connected" @select="send('SELECT_HERO',{option:$event})"/><WarlordsCamp :profile="state.profile" :hero="selectedHero" :busy="pending||!connected" @upgrade="send('UPGRADE',{option:$event})"/></aside>
      </div>
      <template v-else>
        <div class="battle-fighters">
          <WarlordFighter v-for="(fighter,index) in fighters" :key="fighter.id" :fighter="fighter" :side="index" :mine="fighter.id===game.session.player.id" :active="(state.phase==='BATTLE'||animating)&&actingId===fighter.id"/>
          <div v-if="shot" :key="shot.key" class="attack-flight" :class="{ reverse:shot.actor===state.fighters[1]?.id }" aria-hidden="true"><span class="projectile">➤</span><span class="damage-pop">{{ shot.damage?`−${shot.damage}`:'⛨' }}</span></div>
        </div>
        <div v-if="state.phase==='RESULT'&&!animating" class="battle-result war-panel" :class="{ victory:won }" role="status"><div><span class="war-eyebrow">{{ won?'KHẢI HOÀN':'TẠM LUI BINH' }}</span><h2>{{ won?'Ngọn cờ của bạn đã chiến thắng':`${winnerName} giành chiến thắng` }}</h2><p>{{ mode==='CAMPAIGN'&&won?state.chapter?.victory:state.notice }}</p><p v-if="reward" class="result-rewards">Phần thưởng trận: ◈ {{ reward.gold }} vàng · ✦ {{ reward.experience }} XP <small v-if="mode==='CAMPAIGN'&&won">(chưa gồm thưởng lần đầu qua chương)</small></p><p v-if="state.practice" class="war-muted">Trận luyện tập với bot, không cộng vàng và XP.</p><p v-if="state.rewardsPending" class="war-error">{{ state.notice }}</p></div><div class="result-actions"><button v-if="state.rewardsPending" class="war-button" :disabled="pending||!connected" @click="send('CLAIM')">Lưu lại phần thưởng</button><template v-else-if="mode==='CAMPAIGN'"><button class="war-button" :disabled="pending||!connected" @click="send('MAP')">Về bản đồ / nâng cấp</button><button v-if="!won" class="war-button secondary" :disabled="pending||!connected" @click="send('START',{chapter:state.chapter?.id})">Thử lại chương</button></template><button v-else-if="room?.hostId===game.session.player.id" class="war-button" :disabled="!connected||(room?.players.length??0)<2" @click="game.restart">Đấu lại</button><p v-else class="war-muted">Chủ phòng có thể bắt đầu ván mới.</p></div></div>
        <div class="combat-layout">
          <section class="combat-board-panel war-panel">
            <div class="board-heading"><span class="war-eyebrow">THẾ TRẬN · LƯỢT {{ state.turnNumber }}<small class="last-action" :title="lastAction">{{ selected?`Đã chọn ${resourceNames[board[selected.row][selected.column]]} · chọn một ô kề bên`:hint.length?'Đổi hai ô có viền sáng để tạo bộ ba':lastAction || (state.chapter ? `AI ${state.chapter.botLevel}/4 · ${state.chapter.botStyle}` : 'Hai tướng cùng thao tác trên bàn') }}</small></span><button class="war-button secondary small" :disabled="!canAct" @click="showHint">Gợi ý một nước</button></div>
            <p id="board-keyboard-help" class="visually-hidden">Dùng phím mũi tên để chọn ô, Enter hoặc Space để đánh dấu hai ô kề nhau. Escape bỏ chọn. Tab chuyển sang kỹ năng.</p>
            <div ref="grid" class="combat-grid" :class="{ waiting:!myTurn, resolving:animating }" :aria-busy="animating" role="group" aria-label="Bàn chiến đấu 8 hàng 8 cột" aria-describedby="board-keyboard-help">
              <template v-for="(row,r) in board" :key="r"><button v-for="(value,c) in row" :key="c" class="combat-tile" :class="[`type-${value}`,{ selected:selected?.row===r&&selected?.column===c,hint:hint.includes(`${r},${c}`),'move-cell':movingCells.includes(`${r},${c}`),'opponent-cell':opponentMoving&&movingCells.includes(`${r},${c}`) }]" :disabled="!canAct||value===0" :tabindex="focusedCell===r*8+c?0:-1" :aria-label="`Hàng ${r+1}, cột ${c+1}: ${resourceNames[value]||'ô trống'}`" :aria-pressed="selected?.row===r&&selected?.column===c" @focus="focusedCell=r*8+c" @keydown="navigateBoard($event,r,c)" @click="choose(r,c)"><WarlordTile v-if="value" :value="value"/></button></template>
            </div>
          </section>
          <aside class="combat-sidebar">
            <section class="war-panel skills-panel" aria-label="Kỹ năng chiến đấu">
              <span class="war-eyebrow">BINH PHÁP · {{ me?.energy??0 }}/100 NĂNG LƯỢNG</span><h2>Chọn thế ra quân</h2>
              <button v-for="skill in state.skills" :key="skill.id" class="skill-button" :class="{ ready:!skillBlockReason(skill) }" :disabled="!!skillBlockReason(skill)" :title="`${skill.name}: ${skillEffect(skill)} · ${skillBlockReason(skill)||'Sẵn sàng, dùng một lượt'}`" @click="send('SKILL',{option:skill.id})">
                <span class="skill-icon" aria-hidden="true">{{ skill.id==='STRIKE'?'⚔':skill.id==='HEAL'?'✚':'⛨' }}</span>
                <span><strong>{{ skill.name }}</strong><small>{{ skillEffect(skill) }}</small><small class="skill-readiness">{{ skillBlockReason(skill)||'Sẵn sàng ra chiêu' }}</small></span><em>✦ {{ skill.cost }}</em>
              </button><p class="war-muted skill-help">Mỗi kỹ năng dùng một lượt. Ghép ô năng lượng để tích thêm; tối đa 100.</p>
            </section>
            <section class="war-panel battle-field-guide" aria-label="Tài nguyên trên bàn">
              <span class="war-eyebrow">MỖI Ô GHÉP ĐƯỢC</span>
              <div v-for="item in resourceGuide" :key="item.value" class="resource-row"><WarlordTile :value="item.value"/><span>{{ resourceNames[item.value] }}</span><strong>{{ item.effect }}</strong></div>
              <p v-if="attrition" class="attrition-warning">Hao quân: −{{ attrition }} HP sau khi ra quân, xuyên khiên.</p>
              <p v-else class="war-muted">Từ lượt 40, hao quân trừ HP sau mỗi lần ra quân.</p>
            </section>
          </aside>
        </div>
      </template>
      <dialog ref="details" class="battle-details war-panel" aria-labelledby="battle-details-heading" @close="confirmRetreat=false;leaveAfterRetreat=false">
        <div class="details-heading"><h2 id="battle-details-heading">Hướng dẫn & diễn biến</h2><button class="war-button secondary small" autofocus @click="details?.close()">Đóng ✕</button></div>
            <div class="resource-guide"><span v-for="item in resourceGuide" :key="item.value"><WarlordTile :value="item.value"/><small>{{ resourceNames[item.value] }}</small><strong>{{ item.effect }}</strong></span></div>
            <p class="board-help">Đổi hai ô kề nhau để ghép từ 3 hình cùng loại. Liên hoàn tăng sát thương kiếm thêm 25% mỗi đợt, tối đa 75%. Đổi hợp lệ hoặc dùng kỹ năng kết thúc lượt; nước không tạo bộ ba không mất lượt.</p>
            <p class="board-help">Bàn phím: ← ↑ ↓ → di chuyển, Enter / Space chọn ô, Esc bỏ chọn. Có thể dùng nút Gợi ý một nước khi đến lượt bạn.</p>
            <p class="board-help">Mỗi lượt có 45 giây. Quá giờ mất 12 HP; bỏ ba lượt liên tiếp sẽ thua. Từ lượt 40 có hao quân xuyên khiên.<strong v-if="me?.missedTurns"> Bạn đã bỏ {{ me.missedTurns }}/3 lượt liên tiếp.</strong></p>
            <section class="war-panel battle-log"><span class="war-eyebrow">TIN TỪ CHIẾN TRƯỜNG</span><h2>Diễn biến</h2><ol v-if="log.length"><li v-for="(item,index) in log.slice(0,8)" :key="index"><strong>{{ item.actor }}</strong><span>{{ item.text }}</span></li></ol><p v-else class="war-muted">Đội quân đang chờ nước đi đầu tiên.</p></section>
            <p v-if="state.notice&&state.phase==='BATTLE'" class="war-notice">{{ state.notice }}</p>
            <section v-if="confirmRetreat&&state.phase==='BATTLE'" class="retreat-confirm" aria-label="Xác nhận rời trận">
              <strong>{{ mode==='CAMPAIGN'?(leaveAfterRetreat?'Rời phòng và giữ hành trình?':'Rút quân khỏi trận này?'):'Đầu hàng trận này?' }}</strong>
              <p>{{ mode==='CAMPAIGN'?(leaveAfterRetreat?'Chiến dịch được giữ ở lượt đã lưu gần nhất. Chọn Tiếp tục trận tại doanh trại để trở lại.':'Trận đang đánh sẽ kết thúc và không nhận thưởng trận bỏ dở. Tiến độ các chương đã hoàn thành vẫn được giữ.'):'Rời trận khi đối thủ còn trong phòng sẽ được tính là thua.' }}</p>
              <div><button class="war-button secondary small" @click="confirmRetreat=false;leaveAfterRetreat=false">Tiếp tục chiến đấu</button><button class="war-button small" :disabled="!leaveAfterRetreat&&(pending||!connected)" @click="retreat">{{ leaveAfterRetreat?'Rời phòng':'Xác nhận rút quân' }}</button></div>
            </section>
            <button v-else-if="state.phase==='BATTLE'" class="war-button secondary small retreat" :disabled="pending||!connected" @click="requestRetreat()">{{ mode==='CAMPAIGN'?'Rút quân · không nhận thưởng':'Đầu hàng trận này' }}</button>
      </dialog>
      <p class="war-history-note">Chiến dịch và kỹ năng là hư cấu lấy cảm hứng từ thời 12 sứ quân. Tên địa danh dùng theo bối cảnh lịch sử; lộ trình màn không phải niên biểu.</p>
    </template>
  </div>
</template>
<style scoped>
.last-action{display:block;max-width:420px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:.65rem;letter-spacing:0;text-transform:none;color:#b9c7b9;margin-top:3px}.combat-tile.move-cell{outline:3px solid #f7d17a;outline-offset:-3px;box-shadow:inset 0 0 18px #f7d17a80;z-index:2}.combat-tile.opponent-cell{outline-color:#89d4ee;box-shadow:inset 0 0 20px #70bedb99}.in-combat .board-heading{height:40px}.in-combat .combat-grid{width:min(100%,calc(100cqh - 44px))}.in-combat .board-heading .last-action{font-size:.57rem;max-width:min(320px,48vw)}
.room-title {font-size:.8rem;}.battle-status {display:flex;align-items:center;gap:.65rem;background:#171e21;border:1px solid #a7895133;border-radius:10px;padding:.85rem 1rem;font-size:.85rem;line-height:1.5;min-height:56px;}.battle-status strong {margin-left:auto;color:#e2c47b;white-space:nowrap;}.battle-status .urgent {color:#ffac94;}.status-dot {width:7px;height:7px;flex-shrink:0;border-radius:50%;background:#89929c;}.status-dot.live {background:#b8d59b;box-shadow:0 0 9px #abc58677;}.battle-fighters {display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem;position:relative;margin:1rem 0;}.attack-flight {position:absolute;top:32%;left:15%;right:15%;height:35px;pointer-events:none;z-index:4;}.projectile {display:block;width:45px;color:#ffe5a3;font-size:2rem;filter:drop-shadow(0 0 6px #e69b52);animation:war-shot .3s ease-in forwards;}.damage-pop {position:absolute;right:0;top:-10px;font-size:1.4rem;font-weight:900;color:#ffd1a6;text-shadow:0 2px 3px #000;animation:war-damage .42s ease-out forwards;}.attack-flight.reverse {transform:scaleX(-1);}.attack-flight.reverse .damage-pop {transform:scaleX(-1);}.combat-layout {display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:1rem;align-items:start;}.combat-board-panel {padding:clamp(.6rem,2vw,1.2rem);}.board-heading {display:flex;justify-content:space-between;align-items:center;gap:.5rem;margin-bottom:.8rem;}.board-heading>span {font-size:.6rem;}.combat-grid {display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:clamp(3px,.6vw,6px);padding:clamp(5px,1vw,10px);border:1px solid #b7985c66;border-radius:12px;background:linear-gradient(135deg,#302d26,#101b21);box-shadow:inset 0 2px 15px #0006;}.combat-tile {aspect-ratio:1;min-width:0;padding:2px;display:grid;place-items:center;border:1px solid #b89c654a;border-bottom:3px solid #0e141b;border-radius:clamp(5px,1vw,10px);background:linear-gradient(145deg,#54402c,#322b25);opacity:1;cursor:pointer;touch-action:manipulation;box-shadow:inset 0 1px #edd8a32b,0 2px 2px #0005;}.combat-tile:disabled {cursor:default;}.combat-tile:enabled:hover {filter:brightness(1.25);outline:2px solid #efd39a;outline-offset:-2px;}.combat-tile:focus-visible {outline:3px solid white;outline-offset:-3px;}.combat-tile.type-2 {background:linear-gradient(145deg,#315568,#1b3446);}.combat-tile.type-3 {background:linear-gradient(145deg,#724447,#3c2b30);}.combat-tile.type-4 {background:linear-gradient(145deg,#4f6249,#2b3d35);}.combat-tile.type-5 {background:linear-gradient(145deg,#78602f,#473b22);}.combat-tile.type-6 {background:linear-gradient(145deg,#5e466e,#342c47);}.combat-tile.type-0 {background:#182025;border-color:#182025;box-shadow:none;}.combat-tile.selected {outline:3px solid #ffe4a7;outline-offset:-3px;filter:brightness(1.25);transform:scale(.94);}.combat-tile.hint {outline:2px dashed #d9f3ee;outline-offset:-3px;}.resource-guide {display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:3px;margin:.8rem 0;}.resource-guide>span {display:flex;flex-direction:column;align-items:center;gap:3px;}.resource-guide .resource-icon {width:28px;height:28px;}.resource-guide small {font-size:.6rem;color:#c4b697;text-align:center;}.board-help {font-size:.73rem;line-height:1.6;color:#b4a58b;margin-bottom:0;}.combat-sidebar {display:flex;flex-direction:column;gap:1rem;}.skills-panel h2,.battle-log h2 {margin:.5rem 0 1rem;}.skill-button {display:flex;align-items:center;gap:.7rem;width:100%;padding:.8rem .6rem;margin:.5rem 0;border:1px solid #a88b5159;border-radius:9px;background:#1c2529;color:#e9d9ba;text-align:left;min-height:60px;}.skill-button:disabled {opacity:.45;cursor:not-allowed;}.skill-button:enabled:hover {background:#30372f;border-color:#d7b67a;}.skill-button>span:not(.skill-icon) {display:flex;flex:1;flex-direction:column;gap:.4rem;}.skill-button strong {font-size:.85rem;}.skill-button small {font-size:.68rem;color:#af9e82;}.skill-icon {font-size:1.7rem;color:#e8bd71;}.skill-button em {font-style:normal;font-size:.73rem;color:#91c5db;}.skill-help {font-size:.68rem;line-height:1.6;}.battle-log ol {list-style:none;padding:0;margin:0;display:flex;flex-direction:column;gap:.8rem;max-height:300px;overflow:auto;}.battle-log li {display:flex;flex-direction:column;gap:.3rem;font-size:.72rem;line-height:1.5;border-left:2px solid #bc99514d;padding-left:.6rem;}.battle-log li strong {color:#ceaf74;font-size:.63rem;}.battle-log li span {color:#bfb29a;}.battle-log p {font-size:.75rem;}.retreat {width:100%;}.battle-result {display:flex;align-items:center;justify-content:space-between;gap:1rem;margin:1rem 0;border-color:#a3664c;background:radial-gradient(ellipse at 0 0,#52332b,#1d2224);}.battle-result.victory {border-color:#a98d4e;background:radial-gradient(ellipse at 0 0,#4a4830,#1d2423);}.battle-result h2 {margin:.5rem 0;}.battle-result p {font-size:.8rem;line-height:1.7;}.result-rewards {color:#e6c780;}.result-rewards small {display:block;font-size:.66rem;color:#b8aa8c;}.result-actions {display:flex;flex-direction:column;gap:.5rem;min-width:190px;}.invite-copy {font-size:.8rem;color:#d8c18d;margin:1rem 0;}.invite-copy input {width:100%;font-size:.8rem;background:#171e23;border:1px solid #b38b4e55;color:#ead6a9;padding:.6rem;border-radius:5px;}
@keyframes war-shot {from{margin-left:0;opacity:1;}to{margin-left:calc(100% - 45px);opacity:.8;}}
@keyframes war-damage {from{top:0;opacity:1;}to{top:-25px;opacity:0;}}
@media(max-width:900px){.combat-layout {grid-template-columns:minmax(0,1fr) 250px;}.skills-panel,.battle-log {padding:1rem;}}
@media(max-width:700px){.combat-layout {grid-template-columns:1fr;}.combat-sidebar {display:grid;grid-template-columns:1fr 1fr;}.retreat {grid-column:1/-1;}.battle-result {flex-direction:column;align-items:stretch;}.result-actions {flex-direction:row;flex-wrap:wrap;}.result-actions>* {flex:1;}.battle-fighters {gap:.6rem;}.combat-board-panel {padding:.7rem;}}
@media(max-width:430px){.combat-sidebar {grid-template-columns:1fr;}.battle-status {font-size:.75rem;padding:.65rem;}.resource-guide small {font-size:.52rem;}.resource-guide .resource-icon {width:24px;height:24px;}}
@media(max-width:560px){.war-header{flex-direction:row;align-items:center;gap:.5rem;margin-bottom:.7rem;}.war-header h1{font-size:1.05rem;line-height:1.3;margin:.3rem 0;}.war-header .war-eyebrow{font-size:.47rem;letter-spacing:.1em;}.war-header-actions{width:auto;flex:0 0 auto;}.war-header-actions .war-button{font-size:.65rem;padding:.45rem .55rem;}.room-title{display:none;}.battle-status{min-height:0;font-size:.68rem;padding:.6rem .7rem;margin:.6rem 0;}.battle-fighters{margin:.7rem 0;gap:.5rem;}.board-heading{margin-bottom:.4rem;}.board-heading>span{font-size:.48rem;letter-spacing:.09em;}.board-heading .war-button{font-size:.6rem;padding:.35rem .5rem;min-height:30px;}.combat-board-panel{padding:.55rem;}.resource-guide{margin:.55rem 0 .2rem;}.board-help{font-size:.62rem;margin:.4rem 0 0;}.skills-panel{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.4rem;padding:.7rem;}.skills-panel>.war-eyebrow{grid-column:1/-1;font-size:.52rem;}.skills-panel h2,.skill-help{display:none;}.skill-button{flex-direction:column;gap:.3rem;margin:0;padding:.55rem .25rem;text-align:center;min-height:90px;}.skill-icon{font-size:1.2rem;}.skill-button>span:not(.skill-icon){gap:.3rem;}.skill-button strong{font-size:.68rem;}.skill-button small,.skill-button em{font-size:.57rem;}}

/* The battle owns the viewport; supporting information lives in a dialog. */
.warlords-battle {height:100%;min-height:0;overflow:auto;}
.in-combat {display:flex;flex-direction:column;gap:6px;padding:8px 12px;overflow:hidden;position:relative;max-width:1100px;}
.in-combat .war-header {flex:none;display:flex;flex-direction:row;align-items:center;gap:8px;margin:0;min-height:38px;}
.in-combat .war-header h1 {font-size:1.1rem;line-height:1.2;margin:2px 0;}
.in-combat .war-header .war-eyebrow {font-size:.48rem;letter-spacing:.1em;}
.in-combat .room-title {display:none;}
.in-combat .war-header-actions {width:auto;gap:5px;flex-shrink:0;}
.in-combat .war-header-actions button {font-size:.65rem;padding:7px 9px;min-height:32px;}
.in-combat .battle-status {flex:none;min-height:30px;padding:4px 9px;margin:0;font-size:.7rem;}
.in-combat .battle-fighters {flex:none;gap:8px;margin:0;}
.in-combat :deep(.fighter-card) {padding:7px 9px;border-radius:10px;}
.in-combat :deep(.fighter-heading) {min-height:30px;gap:6px;}
.in-combat :deep(.general-portrait) {width:29px;height:32px;}
.in-combat :deep(.fighter-name) {gap:0;}
.in-combat :deep(.fighter-name>span) {font-size:.4rem;}
.in-combat :deep(.fighter-name>strong) {font-size:.8rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.in-combat :deep(.fighter-name>small) {display:none;}
.in-combat :deep(.fighter-bar-label) {font-size:.6rem;margin:2px 0 1px;line-height:1.2;}
.in-combat :deep(.fighter-meter) {height:6px;}
.in-combat :deep(.fighter-meter.energy) {height:4px;}
.in-combat :deep(.fighter-loot) {margin-top:3px;font-size:.55rem;line-height:1.2;}
.in-combat :deep(.turn-badge) {top:-7px;right:0;font-size:.38rem;padding:2px 4px;}
.in-combat .combat-layout {flex:1;min-height:0;grid-template-columns:minmax(0,1fr) 230px;align-items:stretch;gap:8px;}
.in-combat .combat-board-panel {min-height:0;padding:6px;container-type:size;display:flex;flex-direction:column;align-items:center;}
.in-combat .board-heading {width:100%;height:30px;flex-shrink:0;margin:0 0 4px;}
.in-combat .board-heading>span {font-size:.5rem;letter-spacing:.06em;}
.in-combat .board-heading button {padding:4px 8px;font-size:.6rem;min-height:28px;}
.in-combat .combat-grid {width:min(100%,calc(100cqh - 34px));aspect-ratio:1;flex-shrink:0;gap:4px;padding:5px;overflow:hidden;}
.in-combat .combat-tile {position:relative;overflow:visible;transition:background .15s;transform:none;border-radius:6px;}
.in-combat .combat-tile svg {position:relative;z-index:1;pointer-events:none;}
.in-combat .combat-tile:disabled {opacity:1;}
.in-combat .combat-sidebar {min-height:0;gap:6px;}
.in-combat .skills-panel {padding:10px;}
.in-combat .skills-panel>.war-eyebrow {font-size:.52rem;letter-spacing:.05em;}
.in-combat .skills-panel h2 {font-size:1rem;margin:6px 0;}
.in-combat .skill-button {min-height:56px;margin:6px 0;padding:8px;gap:6px;}
.in-combat .skill-button>span:not(.skill-icon) {gap:2px;}
.in-combat .skill-button strong {font-size:.75rem;}
.in-combat .skill-help {margin:6px 0 0;}
.in-combat>.war-history-note {display:none;}
.battle-details {position:fixed;inset:0;margin:auto;width:min(540px,calc(100% - 24px));max-height:85dvh;overflow:auto;color:#e9d9ba;z-index:100;}
.battle-details::backdrop {background:#050c16bb;backdrop-filter:blur(5px);}
.details-heading {display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:14px;}
.details-heading h2 {font-size:1rem;}
.battle-details .battle-log {margin:15px 0;padding:12px;}
.battle-details .board-help {margin:10px 0;}
.battle-error,.invite-copy {position:absolute;top:54px;left:12px;right:12px;z-index:20;padding:10px;background:#32262a;border:1px solid #b58a5c;border-radius:8px;font-size:.75rem;}
.in-combat .battle-result {position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(620px,calc(100% - 24px));max-height:85%;overflow:auto;z-index:25;margin:0;box-shadow:0 0 0 100vmax #060c18c9;}
@media(max-width:700px) and (orientation:portrait) {
 .in-combat {padding:6px;gap:5px;}
 .in-combat .war-header h1 {font-size:.88rem;}
 .in-combat .war-header-actions button {font-size:.57rem;padding:6px;}
 .in-combat .war-header .war-eyebrow {font-size:.4rem;}
 .in-combat .combat-layout {grid-template-columns:1fr;grid-template-rows:minmax(0,1fr) 99px;gap:5px;}
 .in-combat .combat-sidebar {display:block;}
 .in-combat .skills-panel {height:100%;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));grid-template-rows:15px minmax(0,1fr);gap:4px;padding:5px;}
 .in-combat .skills-panel>.war-eyebrow {grid-column:1/-1;font-size:.47rem;}
 .in-combat .skills-panel h2,.in-combat .skill-help {display:none;}
 .in-combat .skill-button {min-height:0;margin:0;padding:3px;flex-direction:column;gap:1px;text-align:center;}
 .in-combat .skill-icon {display:none;}
 .in-combat .skill-button>span:not(.skill-icon) {flex:0;gap:1px;}
 .in-combat .skill-button strong {font-size:.7rem;}
 .in-combat .skill-button small,.in-combat .skill-button em {font-size:.58rem;}
 .in-combat .combat-grid {gap:3px;padding:4px;}
}
@media(max-height:500px) and (orientation:landscape) {
 .in-combat {display:grid;grid-template-columns:minmax(190px,1fr) minmax(0,2fr);grid-template-rows:35px 31px minmax(0,1fr);gap:5px;padding:5px;max-width:100%;}
 .in-combat .war-header {grid-column:1/-1;min-height:0;}
 .in-combat .war-header h1 {font-size:.85rem;}
 .in-combat .war-header .war-eyebrow {display:none;}
 .in-combat .battle-status {grid-column:1;grid-row:2;font-size:.6rem;line-height:1.2;}
 .in-combat .battle-fighters {grid-column:1;grid-row:3;display:flex;flex-direction:column;gap:5px;}
 .in-combat .combat-layout {grid-column:2;grid-row:2/4;grid-template-columns:minmax(0,1fr) 140px;}
 .in-combat .skills-panel {display:flex;flex-direction:column;padding:5px;}
 .in-combat .skills-panel h2,.in-combat .skill-help,.in-combat .skill-icon {display:none;}
 .in-combat .skill-button {min-height:50px;padding:5px;margin:4px 0;flex-direction:row;text-align:left;}
 .in-combat .skill-button strong {font-size:.65rem;}
 .in-combat .skill-button small,.in-combat .skill-button em {font-size:.55rem;}
}
/* Reserve space for the current action even in the compact battle layout. */
.in-combat .board-heading{height:40px;min-width:0}
.in-combat .board-heading>span{min-width:0;flex:1}
.in-combat .board-heading>button{flex-shrink:0}
.in-combat .combat-grid{width:min(100%,calc(100cqh - 44px))}
@media(max-width:430px){.in-combat .board-heading .last-action{max-width:44vw;font-size:.5rem}}
.visually-hidden{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0}
.battle-status.your-turn{border-color:#b6bd7159;background:linear-gradient(90deg,#273228,#171e21)}
.battle-status.reconnecting{border-color:#d7ac6666}
.turn-message{flex:1;min-width:0}
.turn-clock{flex:0 0 72px;text-align:right;color:#e2c47b;font-variant-numeric:tabular-nums}
.turn-clock strong{font-size:.95rem;line-height:1.2}
.turn-clock small{font-size:.58rem;color:#b8b097;font-weight:400}
.turn-clock-track{display:block;margin-top:3px;height:3px;border-radius:4px;overflow:hidden;background:#090f12}
.turn-clock-track>span{display:block;height:100%;background:#cbb570;transition:width .5s linear}
.turn-clock.urgent strong{color:#ffb198}
.turn-clock.urgent .turn-clock-track>span{background:#ef9c78}
.in-combat .combat-sidebar{overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#8c7849 transparent}
.in-combat .skill-button{min-height:72px}
.in-combat .skill-button:disabled{opacity:1;background:#192126;border-color:#766c4d55}
.skill-button:disabled .skill-icon,.skill-button:disabled em{color:#98a6a7}
.skill-button:disabled strong{color:#c3bcab}
.skill-button.ready{border-color:#a8bd8066;background:linear-gradient(115deg,#303d2d,#1c2529)}
.skill-button .skill-readiness{font-size:.6rem;color:#d2b384;line-height:1.25}
.skill-button.ready .skill-readiness{color:#b9d29c}
.battle-field-guide{padding:12px 10px}
.battle-field-guide>.war-eyebrow{display:block;font-size:.5rem;letter-spacing:.06em;margin-bottom:8px}
.resource-row{display:flex;align-items:center;gap:7px;padding:4px 0;font-size:.64rem;color:#c7baa2}
.resource-row :deep(.resource-icon){width:22px;height:22px;flex-shrink:0}
.resource-row strong{margin-left:auto;font-size:.6rem;color:#ded0af;font-weight:500;text-align:right}
.battle-field-guide p{font-size:.65rem;line-height:1.55;margin:9px 0 0;border-top:1px solid #96805238;padding-top:8px}
.attrition-warning{color:#f0b694}
.resource-guide strong{font-size:.6rem;font-weight:500;color:#e0c98d;text-align:center}
.retreat-confirm{padding:14px;border:1px solid #b47c5566;border-radius:10px;background:#392b2644}
.retreat-confirm>strong{color:#edc39a}
.retreat-confirm p{font-size:.78rem;line-height:1.7;color:#ccbaa2}
.retreat-confirm>div{display:flex;flex-wrap:wrap;gap:8px}
.retreat-confirm button{flex:1;min-width:130px}
@media(max-width:700px) and (orientation:portrait){
 .battle-field-guide{display:none}
 .in-combat .combat-sidebar{overflow:visible}
 .in-combat .skill-button{min-height:0}
 .in-combat .skill-button .skill-readiness{font-size:.53rem;line-height:1.2}
 .in-combat .skills-panel{grid-template-rows:14px minmax(0,1fr)}
 .turn-clock{flex-basis:58px}
 .turn-clock strong{font-size:.8rem}
}
@media(max-width:560px){.battle-details .resource-guide{grid-template-columns:repeat(3,minmax(0,1fr));gap:12px 6px}.battle-details .board-help{font-size:.74rem}.resource-guide small{font-size:.65rem}}
@media(max-height:500px) and (orientation:landscape){
 .battle-field-guide{display:none}
 .in-combat .skill-button{min-height:55px}
 .in-combat .skill-button .skill-readiness{font-size:.5rem}
 .turn-clock{flex-basis:48px}
 .turn-clock strong{font-size:.7rem}
 .turn-clock small{font-size:.45rem}
}
@media(prefers-reduced-motion:reduce){.turn-clock-track>span{transition:none}.attack-flight,.projectile,.damage-pop{animation:none}}
</style>
