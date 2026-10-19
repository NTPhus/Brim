<script setup lang="ts">
import { computed,onMounted,onUnmounted,ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBoardSession } from '@/composables/useBoardSession'
import { renderBattle } from '@/game/vietnam/render'
import type { CampaignState } from '@/game/vietnam/types'
import '@/game/vietnam/style.css'
const router=useRouter(),game=useBoardSession<CampaignState>('VIETNAM_CAMPAIGN')
const {state,connected,error,finished}=game
const canvas=ref<HTMLCanvasElement|null>(null),camera={x:0,y:0,scale:1}
const me=computed(()=>state.value?.units[0]),goal=computed(()=>state.value?.objectives[state.value.objective])
const tactical=computed(()=>me.value?.tacticalView)
let pendingCommand:{commandSeq:number;action:string}|null=null,commandSeq=0
const keys=new Set<string>();let aimX=500,aimY=500,firing=false,frame=0,timer:number|undefined
function send(){if(!connected.value||finished.value)return;game.move({dx:Number(keys.has('KeyD')||keys.has('ArrowRight'))-Number(keys.has('KeyA')||keys.has('ArrowLeft')),dy:Number(keys.has('KeyS')||keys.has('ArrowDown'))-Number(keys.has('KeyW')||keys.has('ArrowUp')),aimX,aimY,fire:firing||keys.has('Space'),crouch:keys.has('KeyC'),action:pendingCommand?.action??'',commandSeq:pendingCommand?.commandSeq??0})}
function issue(next:string){if(pendingCommand||finished.value||!connected.value)return;commandSeq=Math.max(commandSeq,state.value?.lastCommandSeq??0)+1;pendingCommand={commandSeq,action:next};send()}
function down(e:KeyboardEvent){if((e.target as HTMLElement)?.closest('input,textarea,select'))return;if(['Space','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code))e.preventDefault();keys.add(e.code);if(!e.repeat){if(e.code==='KeyE')issue('INTERACT');if(e.code==='KeyR')issue('RELOAD');if(e.code==='KeyF')issue('REPAIR')}send()}
function up(e:KeyboardEvent){keys.delete(e.code);send()}
function stop(){keys.clear();firing=false;send()}
function visibility(){if(document.hidden)stop()}
function pointer(e:PointerEvent){const rect=canvas.value?.getBoundingClientRect();if(rect){aimX=camera.x+(e.clientX-rect.left)/camera.scale;aimY=camera.y+(e.clientY-rect.top)/camera.scale}}
function draw(t:number){if(pendingCommand&&(state.value?.lastCommandSeq??0)>=pendingCommand.commandSeq)pendingCommand=null;if(canvas.value&&state.value)renderBattle(canvas.value,state.value,camera,t);frame=requestAnimationFrame(draw)}
function mobile(key:string,e:PointerEvent){(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);keys.add(key);send()}
onMounted(()=>{frame=requestAnimationFrame(draw);timer=window.setInterval(send,150);window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',stop);document.addEventListener('visibilitychange',visibility)})
onUnmounted(()=>{cancelAnimationFrame(frame);window.clearInterval(timer);stop();window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',stop);document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
  <main class="vietnam battle-view">
    <header><div><span class="campaign-eyebrow">DẤU CHÂN VIỆT · {{ state?.chapter.date }}</span><h1>{{ state?.chapter.title ?? 'Đang mở chiến dịch…' }}</h1></div><button @click="router.push('/vietnam')">← Hồ sơ chiến dịch</button></header>
    <div class="battle-status"><span>{{ connected?'Đã kết nối':'Đang kết nối lại…' }}</span><span>{{ me?.hp ?? 0 }} / {{ me?.maxHealth ?? 100 }} HP</span><span>{{ state?.reload?`Nạp đạn ${Math.ceil(state.reload/10)}s`:`Đạn sẵn ${state?.ammo ?? 0} · dự trữ ${state?.reserveAmmo ?? 0}` }}</span><span>{{ state?.layer===1?'DƯỚI ĐỊA ĐẠO':'MẶT ĐẤT' }}</span><span>{{ Math.ceil((state?.ticksLeft ?? 0)/10) }}s</span></div>
    <div v-if="tactical" class="battle-status"><span>Tank nặng · tiến 45 / lùi 25</span><span>Nhiên liệu {{ Math.ceil(tactical.fuel/tactical.fuelCapacity*100) }}%</span><span>{{ tactical.repairKits }} bộ sửa</span><span>{{ tactical.settled?'Nòng ổn định':'Nòng chưa ổn định' }}</span><span v-if="tactical.repairRemainingMs">Sửa xe {{ (tactical.repairRemainingMs/1000).toFixed(1) }}s</span></div>
    <div class="battle-canvas"><canvas ref="canvas" tabindex="0" aria-label="Chiến trường, WASD di chuyển, chuột ngắm, Space bắn, E tương tác" @pointermove="pointer" @pointerdown.prevent="pointer($event);firing=true;($event.currentTarget as HTMLElement).setPointerCapture($event.pointerId);send()" @pointerup="firing=false;send()" @pointercancel="stop" @lostpointercapture="firing=false" @contextmenu.prevent />
      <aside class="objective-panel"><span class="campaign-eyebrow">NHIỆM VỤ {{ (state?.objective ?? 0)+1 }}/{{ state?.objectives.length ?? 0 }}</span><p>{{ goal?.label ?? state?.notice }}</p><small v-if="goal">Điểm vàng trên bản đồ · {{ goal.layer?'dưới hầm':'mặt đất' }}</small><p v-if="state?.notice">{{ state.notice }}</p></aside>
      <section v-if="finished" class="campaign-result"><h2>{{ state?.winner?'Nhiệm vụ hoàn thành':'Tập hợp lại đội hình' }}</h2><p>{{ state?.notice }}</p><button @click="game.restart">Chơi lại màn</button><button @click="router.push('/vietnam')">Về chiến dịch →</button></section>
    </div>
    <div class="battle-controls"><button v-for="[key,label] in [['KeyW','↑'],['KeyA','←'],['KeyS','↓'],['KeyD','→']]" :key="key" @pointerdown.prevent="mobile(key,$event)" @pointerup="keys.delete(key);send()" @pointercancel="stop" @lostpointercapture="keys.delete(key);send()">{{ label }}</button><button @click="issue('INTERACT')">E · Tương tác / cửa hầm</button><button @pointerdown.prevent="mobile('KeyC',$event)" @pointerup="keys.delete('KeyC');send()" @pointercancel="stop" @lostpointercapture="keys.delete('KeyC');send()">C · Khom người</button><button v-if="tactical" @click="issue('REPAIR')">F · Sửa xe</button><button @click="issue('RELOAD')">R · Nạp đạn</button><button @pointerdown.prevent="mobile('Space',$event)" @pointerup="keys.delete('Space');send()" @pointercancel="stop" @lostpointercapture="keys.delete('Space');send()">Bắn</button></div>
    <p class="battle-help">WASD / mũi tên: lính di chuyển; tank W/S tiến lùi, A/D quay thân · Chuột ngắm · Giữ chuột trái / Space bắn · C khom người giảm sát thương · E tương tác / chui hầm · R nạp đạn hữu hạn · F sửa xe. Đồng đội AI yểm trợ trên mặt đất. Mục tiêu đánh số vàng trên bản đồ.</p>
    <p v-if="error" role="alert">{{ error }}</p>
  </main>
</template>
