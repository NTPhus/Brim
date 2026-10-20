<script setup lang="ts">
import { specialFormations } from '@/types/warlords'
import WarlordTile from './WarlordTile.vue'
defineProps<{ compact?: boolean }>()
</script>
<template>
  <section class="formations-guide" :class="{ compact }" aria-label="Cách tạo và kích hoạt quân đặc biệt">
    <header><span class="war-eyebrow">GHÉP LỚN, ĐỔI THẾ TRẬN</span><h3>Bốn loại kỳ trận</h3></header>
    <ol><li v-for="formation in specialFormations" :key="formation.kind">
      <WarlordTile :value="formation.kind*10+formation.kind"/>
      <div><strong>{{ formation.name }}</strong><small class="formation-recipe">{{ formation.recipe }}</small><span>{{ formation.effect }}</span></div>
    </li></ol>
    <p>Đổi quân đặc biệt với bất kỳ ô kề nào để kích hoạt, kể cả không tạo bộ ba. Vụ nổ chạm quân đặc biệt khác sẽ kích hoạt tiếp; mỗi ô chỉ nhận tài nguyên một lần trong một đợt.</p>
    <small v-if="!compact" class="formation-fiction">Các kỳ trận là binh pháp hư cấu cho trò chơi, không mô tả vũ khí lịch sử.</small>
  </section>
</template>
<style scoped>
.formations-guide{color:#dfcfad}.formations-guide header{margin-bottom:12px}.formations-guide h3{font:500 1.1rem Georgia,serif;margin:7px 0}.formations-guide .war-eyebrow{font-size:.52rem;letter-spacing:.08em}.formations-guide ol{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;list-style:none;padding:0;margin:0}.formations-guide li{display:flex;align-items:center;gap:9px;padding:11px 9px;background:#14202788;border:1px solid #bba16a40;border-radius:8px;min-width:0}.formations-guide li>.resource-icon{width:40px;height:40px;flex:none}.formations-guide li>div{display:flex;flex-direction:column;gap:5px;min-width:0}.formations-guide strong{font-size:.74rem;color:#edd49a}.formations-guide li small,.formations-guide li span{font-size:.64rem;line-height:1.5;color:#bdc5b2}.formations-guide li span{color:#d4c097}.formations-guide p{font-size:.72rem;line-height:1.8;color:#c4baa5;margin:12px 0 7px}.formation-fiction{display:block;font-size:.62rem;line-height:1.6;color:#a89f8c}.compact header{margin-bottom:8px}.compact h3{font-size:.9rem}.compact ol{grid-template-columns:1fr;gap:5px}.compact li{padding:7px 5px;gap:6px}.compact li>.resource-icon{width:28px;height:28px}.compact li>div{gap:2px}.compact strong{font-size:.66rem}.compact li span{font-size:.6rem}.compact .formation-recipe{display:none}.compact p{font-size:.63rem;margin-bottom:0}
@media(max-width:380px){.formations-guide:not(.compact) ol{grid-template-columns:1fr}.formations-guide li>.resource-icon{width:34px;height:34px}}
</style>
