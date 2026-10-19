<script setup lang="ts">
import { ref,onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/api/session'
import { createRoom,getAllRooms } from '@/api/rooms'
import { useGameSession } from '@/stores/gameSession'
import type { Chapter } from '@/game/vietnam/types'
import '@/game/vietnam/style.css'
const router=useRouter(),session=useGameSession()
const hub=ref<{unlocked:number;chapters:Chapter[]}|null>(null),error=ref(''),busy=ref(false)
async function load(){try{const r=await apiFetch('/api/vietnam');if(!r.ok)throw new Error('Không thể mở hồ sơ chiến dịch.');hub.value=await r.json()}catch(e){error.value=String(e)}}
onMounted(load)
async function enter(chapter:number){
  if(busy.value)return;busy.value=true;error.value=''
  try {
    const rooms=await getAllRooms()
    const existing=rooms.find(r=>r.gameType==='VIETNAM_CAMPAIGN'&&r.hostId===session.player.id&&r.status==='IN_PROGRESS')
    const room=existing??await createRoom({name:`Dấu chân Việt · Màn ${chapter}`,gameType:'VIETNAM_CAMPAIGN',maxPlayers:1,vietnamChapter:chapter})
    session.setCurrentRoom(room);await router.push(`/room/${room.id}`)
  }catch(e){error.value=String(e)}finally{busy.value=false}
}
</script>
<template>
  <main class="vietnam campaign-hub">
    <header class="campaign-hero"><span class="campaign-eyebrow">BRIM · CHIẾN DỊCH LỊCH SỬ VIỆT NAM</span><h1>DẤU CHÂN<br><em>VIỆT</em></h1><p>Không chỉ là một cỗ xe.<br>Là người lính, đồng đội và hành trình qua những dấu mốc lịch sử.</p><button @click="router.push('/')">← Về sảnh</button></header>
    <p v-if="error" role="alert">{{ error }}</p><button v-if="!hub" @click="load">Tải hồ sơ chiến dịch</button>
    <div v-if="hub" class="campaign-chapters">
      <article v-for="c in hub.chapters" :key="c.id" class="campaign-chapter" :class="c.theme.toLowerCase()">
        <div class="chapter-landscape"><span>{{ c.date }}</span><strong>{{ c.location }}</strong><span>{{ c.role==='TANK'?'KÍP XE TĂNG':'BỘ BINH' }}</span></div>
        <section><span class="campaign-eyebrow">HỒ SƠ {{ String(c.id).padStart(2,'0') }}</span><h2>{{ c.title }}</h2><strong>{{ c.character }}</strong><p>{{ c.history }}</p><p>{{ c.mission }}</p>
          <a v-if="c.id===3" href="https://vov.vn/xa-hoi/xe-tang-390-la-bao-vat-quoc-gia-234427.vov" target="_blank" rel="noopener noreferrer">Hồ sơ kíp xe 390 · VOV ↗</a>
          <a :href="c.source" target="_blank" rel="noopener noreferrer">Tư liệu · {{ c.sourceTitle }} ↗</a>
          <button :disabled="busy||c.id>hub.unlocked" @click="enter(c.id)">{{ c.id>hub.unlocked?'Hoàn thành màn trước để mở':busy?'Đang chuẩn bị…':'Vào chiến dịch →' }}</button>
        </section>
      </article>
    </div>
    <p class="history-note">Đây là bản chơi 2D cách điệu, chưa phải phục dựng địa hình hay kiến trúc theo tỷ lệ thực. Sự kiện và nhân vật trong hồ sơ dựa trên tư liệu; tuyến đường, đồng đội hư cấu và nhiệm vụ phụ được sáng tác cho gameplay. Củ Chi là một bối cảnh địa đạo, không được gán thành một trận đánh cụ thể. Tiến trình lưu theo phiên guest hiện tại.</p>
  </main>
</template>
