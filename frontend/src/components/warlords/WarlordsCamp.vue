<script setup lang="ts">
import { heroLevel, upgrades } from '@/types/warlords'
import type { WarlordsProfile } from '@/types/warlords'
defineProps<{ profile: WarlordsProfile; busy?: boolean }>()
defineEmits<{ upgrade: [type: string] }>()
</script>
<template>
  <section class="warlords-camp war-panel">
    <div class="camp-head"><div><span class="war-eyebrow">DOANH TRẠI HOA LƯ</span><h2>Quân đoàn của bạn</h2></div><span class="level-seal">{{ heroLevel(profile) }}<small>CẤP</small></span></div>
    <div class="camp-resources"><strong>◈ {{ profile.gold.toLocaleString() }} <small>vàng</small></strong><span>✦ {{ profile.experience.toLocaleString() }} XP</span><span>⚑ {{ profile.cleared.length }}/12 chương</span></div>
    <progress class="experience-bar" :value="heroLevel(profile) === 20 ? 100 : profile.experience % 100" max="100" aria-label="Kinh nghiệm lên cấp"/>
    <p class="camp-summary">{{ 140 + (heroLevel(profile)-1)*8 + profile.armor*15 }} HP · {{ 5+profile.weapon }} sát thương/kiếm · {{ profile.wins }} thắng</p>
    <div class="upgrade-list">
      <div v-for="upgrade in upgrades" :key="upgrade.id" class="upgrade-row">
        <span class="upgrade-icon" aria-hidden="true">{{ upgrade.icon }}</span><div class="upgrade-description"><strong>{{ upgrade.name }} <small>{{ profile[upgrade.field] }}/5</small></strong><span>{{ upgrade.description }}</span></div>
        <button class="war-button small" :disabled="busy || profile[upgrade.field] >= 5 || profile.gold < upgrade.base*(profile[upgrade.field]+1)" @click="$emit('upgrade',upgrade.id)">{{ profile[upgrade.field]>=5?'Tối đa':`◈ ${upgrade.base*(profile[upgrade.field]+1)}` }}</button>
      </div>
    </div>
    <p class="war-muted camp-footnote">Nâng cấp dùng trong chiến dịch. Đấu tướng PvP có chỉ số ngang nhau. Tiến độ được lưu theo phiên guest hiện tại.</p>
  </section>
</template>
<style scoped>
.camp-head { display:flex;align-items:center;justify-content:space-between;gap:1rem; }.camp-head h2 { margin:.3rem 0; }.level-seal { border:1px solid #c3a261;border-radius:50%;width:54px;height:54px;display:flex;align-items:center;justify-content:center;flex-direction:column;font-family:Georgia,serif;color:#edcf87;font-size:1.2rem; }.level-seal small {font: .5rem sans-serif;letter-spacing:.15em;}
.camp-resources { display:flex;flex-wrap:wrap;gap:.8rem;margin:1rem 0 .6rem;font-size:.85rem; }.camp-resources strong {color:#f0ce79;}.camp-resources small {font-weight:400;}.camp-resources span {color:#d2bfd9;}.experience-bar {width:100%;height:5px;accent-color:#bd94c9;}.camp-summary {font-size:.8rem;color:#baa88d;}
.upgrade-list {display:flex;flex-direction:column;gap:.6rem;margin-top:1rem;}.upgrade-row {display:flex;align-items:center;gap:.65rem;padding:.8rem 0;border-top:1px solid #c6ac7726;}.upgrade-icon {font-size:1.6rem;color:#d8bb7a;min-width:30px;text-align:center;}.upgrade-description {flex:1;display:flex;flex-direction:column;gap:.3rem;font-size:.82rem;}.upgrade-description span {font-size:.72rem;color:#b3a38c;}.upgrade-description small {font-weight:400;color:#d8bb7a;}.camp-footnote {font-size:.7rem;line-height:1.6;margin-bottom:0;}
</style>
