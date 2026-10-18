<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGameSession } from '@/stores/gameSession'
import { createRoom, getAllRooms, joinRoom } from '@/api/rooms'
import { getWarlordsHub, upgradeWarlords } from '@/api/warlords'
import type { WarlordsHub } from '@/types/warlords'
import type { GameRoom } from '@/types/game'
import WarlordsMap from '@/components/warlords/WarlordsMap.vue'
import WarlordsCamp from '@/components/warlords/WarlordsCamp.vue'
import '@/game/warlords.css'
const router=useRouter()
const session=useGameSession()
const hub=ref<WarlordsHub|null>(null)
const rooms=ref<GameRoom[]>([])
const selected=ref(1)
const error=ref('')
const notice=ref('')
const loading=ref(true)
const busy=ref(false)
let poll:number|undefined
let disposed=false
const chapter=computed(()=>hub.value?.chapters.find(item=>item.id===selected.value))
const duelRooms=computed(()=>rooms.value.filter(room=>room.gameType==='WARLORDS_DUEL'&&
  (room.status==='WAITING'&&room.players.length<room.maxPlayers || room.status==='IN_PROGRESS'&&room.players.some(p=>p.id===session.player.id))))
async function refreshRooms(){try{const next=await getAllRooms();if(!disposed)rooms.value=next}catch{/* The refresh button can retry. */}}
async function load(){
  error.value='';loading.value=true
  try{const next=await getWarlordsHub();if(disposed)return;hub.value=next;selected.value=next.profile.unlocked;await refreshRooms()}
  catch(cause){if(!disposed)error.value=cause instanceof Error?cause.message:'Không thể tải chiến dịch.'}
  finally{if(!disposed)loading.value=false}
}
onMounted(()=>{void load();poll=window.setInterval(refreshRooms,5000)})
onUnmounted(()=>{disposed=true;window.clearInterval(poll)})
async function enter(mode:'CAMPAIGN'|'DUEL'){
  if(busy.value)return
  busy.value=true;error.value=''
  try{
    const type=mode==='CAMPAIGN'?'WARLORDS_CAMPAIGN':'WARLORDS_DUEL'
    const existing=rooms.value.find(room=>room.gameType===type&&room.hostId===session.player.id&&['WAITING','IN_PROGRESS'].includes(room.status))
    const room=existing??await createRoom({name:mode==='CAMPAIGN'?'Sơn Hà · Hành trình Hoa Lư':`Đấu tướng · ${session.player.username}`,
      gameType:type,maxPlayers:mode==='CAMPAIGN'?1:2})
    session.setCurrentRoom(room)
    await router.push({path:`/room/${room.id}`,query:mode==='CAMPAIGN'?{chapter:selected.value}:{}})
  }catch(cause){error.value=cause instanceof Error?cause.message:'Không thể tạo bàn chơi.'}
  finally{busy.value=false}
}
async function join(room:GameRoom){
  if(busy.value)return
  busy.value=true;error.value=''
  try{const updated=await joinRoom(room.id,{playerId:session.player.id,username:session.player.username,sessionId:session.player.sessionId});session.setCurrentRoom(updated);await router.push(`/room/${room.id}`)}
  catch{error.value='Không thể vào bàn. Phòng có thể đã đầy hoặc trận đã bắt đầu.';void refreshRooms()}
  finally{busy.value=false}
}
async function upgrade(type:string){
  if(busy.value||!hub.value)return
  busy.value=true;error.value='';notice.value=''
  try{hub.value={...hub.value,profile:await upgradeWarlords(type)};notice.value='Nâng cấp thành công. Đội quân đã sẵn sàng!'}
  catch(cause){error.value=cause instanceof Error?cause.message:'Không thể nâng cấp.'}
  finally{busy.value=false}
}
</script>
<template>
  <div class="warlords warlords-hub">
    <header class="war-header"><div><span class="war-eyebrow">BRIM ORIGINAL · MATCH-3 CHIẾN THUẬT</span><h1>SƠN HÀ <em>Hợp cờ Hoa Lư</em></h1><p class="war-muted">Ghép thế trận. Tích năng lượng. Hợp nhất những ngọn cờ.</p></div><button class="war-button secondary" @click="router.push('/')">Về Brim</button></header>
    <p v-if="error" class="war-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="war-notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="war-muted" role="status">Đang mở bản đồ…</p>
    <button v-else-if="!hub" class="war-button" @click="load">Thử tải lại</button>
    <template v-if="hub">
      <div class="war-campaign-layout">
        <WarlordsMap :chapters="hub.chapters" :profile="hub.profile" :selected="selected" :disabled="busy" @select="selected=$event"/>
        <aside class="war-sidebar">
          <section v-if="chapter" class="war-panel chapter-detail"><span class="war-eyebrow">CHIẾN DỊCH · CHƯƠNG {{ chapter.id }}/12</span><h2>{{ chapter.title }}</h2><strong>{{ chapter.warlord }} · {{ chapter.location }}</strong><p>{{ chapter.story }}</p>
            <div class="chapter-enemy"><span>{{ chapter.encounter }}</span><span>{{ chapter.enemyHp }} HP · {{ chapter.enemyAttack }} công</span></div>
            <div class="chapter-rewards"><span>◈ {{ chapter.gold }} vàng + chiến lợi phẩm</span><span>✦ {{ chapter.experience }} XP</span></div>
            <p v-if="!hub.profile.cleared.includes(chapter.id)" class="first-clear">Thưởng hoàn thành lần đầu: +50 vàng, +30 XP.</p>
            <button class="war-button" :disabled="busy" @click="enter('CAMPAIGN')">{{ busy?'Đang chuẩn bị…':`Vào chương ${chapter.id} →` }}</button>
          </section>
          <WarlordsCamp :profile="hub.profile" :busy="busy" @upgrade="upgrade"/>
        </aside>
      </div>
      <section class="war-panel duel-panel">
        <div><span class="war-eyebrow">ĐẤU TƯỚNG · PVP TRỰC TUYẾN</span><h2>Thử tài cùng một người bạn</h2><p>Cùng một bàn 8×8, luân phiên ra đòn. Cả hai có 140 HP và chỉ số bằng nhau; thắng bằng nước đi và thời điểm dùng kỹ năng.</p></div>
        <button class="war-button" :disabled="busy" @click="enter('DUEL')">Tạo bàn PvP ⚔</button>
      </section>
      <div class="war-section-header"><h2>Bàn đấu đang mở</h2><button class="war-button secondary small" @click="refreshRooms">Làm mới</button></div>
      <div class="duel-room-list"><article v-for="room in duelRooms" :key="room.id" class="war-panel duel-room"><div><strong>{{ room.name }}</strong><span class="war-muted">{{ room.players.length }}/2 người · {{ room.status==='WAITING'?'Chờ đối thủ':'Đang chơi' }}</span></div><button class="war-button small" :disabled="busy" @click="join(room)">{{ room.status==='IN_PROGRESS'?'Vào lại':'Vào bàn' }}</button></article><p v-if="!duelRooms.length" class="war-muted">Chưa có bàn phù hợp. Tạo bàn để mời bạn vào chơi.</p></div>
      <details class="rules-panel war-panel"><summary>Cách chơi Sơn Hà</summary><p>Đổi hai ô kề nhau để ghép ít nhất 3 hình giống nhau. Kiếm gây sát thương; năng lượng dùng kỹ năng; tim hồi HP; khiên đỡ đòn; vàng và quân thư là chiến lợi phẩm nhận khi trận kết thúc. Mỗi lần đổi hợp lệ hoặc dùng kỹ năng tiêu tốn một lượt. Nước không tạo bộ ba không mất lượt.</p><p>Từ lượt 40, quân vừa hành động mất dần HP do hao quân. Mỗi lượt có 45 giây; quá giờ mất 12 HP và đổi lượt, bỏ ba lượt liên tiếp sẽ thua. Bàn PvP có bot là luyện tập, không thưởng vàng/XP.</p></details>
      <p class="war-history-note">Chiến dịch hư cấu lấy cảm hứng từ nhân vật, địa danh thời 12 sứ quân. Thứ tự màn không phải niên biểu; các trận thao luyện không có nghĩa nhân vật ấy bị tiêu diệt. <a href="https://baotanglichsuquocgia.vn/vi/Articles/3096/14529/tam-nhin-chien-luoc-cua-djinh-bo-linh.html" target="_blank" rel="noopener noreferrer">Đọc bối cảnh tại Bảo tàng Lịch sử quốc gia ↗</a></p>
    </template>
  </div>
</template>
<style scoped>
.war-header h1 {letter-spacing:.06em;}.war-header em {display:block;font-size:.85rem;color:#baa88b;font-weight:400;margin-top:.4rem;letter-spacing:.1em;}.war-header p {font-size:.82rem;}.first-clear {font-size:.74rem!important;color:#cfb47c!important;}.duel-panel {display:flex;justify-content:space-between;align-items:center;gap:2rem;margin-top:1.5rem;background:radial-gradient(ellipse at 0 0,#55322966,transparent 70%),#202323;}.duel-panel h2 {margin:.5rem 0;}.duel-panel p {color:#c0b198;font-size:.85rem;line-height:1.7;max-width:650px;}.duel-panel>button {white-space:nowrap;}.duel-room-list {display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.75rem;}.duel-room {display:flex;justify-content:space-between;align-items:center;gap:1rem;}.duel-room>div {display:flex;flex-direction:column;gap:.5rem;font-size:.85rem;}.duel-room span {font-size:.7rem;}.rules-panel {margin-top:1.5rem;}.rules-panel summary {cursor:pointer;font-family:Georgia,serif;}.rules-panel p {font-size:.85rem;color:#c7bba2;line-height:1.8;}
@media(max-width:650px){.duel-panel {flex-direction:column;align-items:stretch;gap:.5rem;}.duel-room-list {grid-template-columns:1fr;}}
</style>
