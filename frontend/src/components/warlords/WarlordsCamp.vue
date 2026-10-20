<script setup lang="ts">
import { computed } from 'vue'
import { heroLevel, upgrades } from '@/types/warlords'
import type { WarlordsHero, WarlordsProfile } from '@/types/warlords'
const props = defineProps<{ profile: WarlordsProfile; hero?: WarlordsHero; busy?: boolean }>()
defineEmits<{ upgrade: [type: string] }>()
const level = computed(() => heroLevel(props.profile))
const nextLevelXp = computed(() => 100 - props.profile.experience % 100)
const stats = computed(() => [
  { label: 'HP tối đa', value: 140 + (level.value - 1) * 8 + props.profile.armor * 15 + (props.hero?.hpBonus ?? 0) },
  { label: 'Công / kiếm', value: 5 + props.profile.weapon + (props.hero?.attackBonus ?? 0) },
  { label: 'Binh pháp', value: props.profile.skill + (props.hero?.skillBonus ?? 0) }
])
</script>
<template>
  <section class="warlords-camp war-panel">
    <div class="camp-head"><div><span class="war-eyebrow">DOANH TRẠI HOA LƯ</span><h2>Rèn quân, luyện tướng</h2></div><span class="level-seal">{{ level }}<small>CẤP</small></span></div>
    <div class="camp-resources"><strong>◈ {{ profile.gold.toLocaleString('vi-VN') }} <small>vàng hiện có</small></strong><span>✦ {{ profile.experience.toLocaleString('vi-VN') }} XP</span></div>
    <div class="experience-label"><span>{{ level === 20 ? 'Đã đạt cấp tối đa' : `Còn ${nextLevelXp} XP đến cấp ${level + 1}` }}</span><small>{{ level === 20 ? 'Cấp 20 / 20' : `${profile.experience % 100} / 100 XP` }}</small></div>
    <progress class="experience-bar" :value="level === 20 ? 100 : profile.experience % 100" max="100" :aria-label="level === 20 ? 'Đã đạt cấp tối đa 20' : `Kinh nghiệm cấp ${level}: ${profile.experience % 100} trên 100`"/>
    <dl class="camp-stats"><div v-for="stat in stats" :key="stat.label"><dt>{{ stat.label }}</dt><dd>{{ stat.value }}</dd></div></dl>
    <p v-if="hero" class="camp-summary">{{ hero.name }} · {{ hero.startingEnergy }} năng lượng và {{ hero.startingShield }} khiên đầu trận.</p>
    <p v-else class="camp-summary">Chỉ số cơ bản trước khi cộng phong cách tướng.</p>
    <div class="upgrade-list">
      <div v-for="upgrade in upgrades" :key="upgrade.id" class="upgrade-row">
        <span class="upgrade-icon" aria-hidden="true">{{ upgrade.icon }}</span>
        <div class="upgrade-description"><strong>{{ upgrade.name }} <small>{{ profile[upgrade.field] }}/5</small></strong><span>{{ upgrade.description }}</span><span class="upgrade-pips" :aria-label="`Đã nâng ${profile[upgrade.field]} trên 5 cấp`"><i v-for="step in 5" :key="step" :class="{ filled:step<=profile[upgrade.field] }"/></span><small v-if="profile[upgrade.field]<5 && profile.gold<upgrade.base*(profile[upgrade.field]+1)" class="upgrade-missing">Cần thêm {{ upgrade.base*(profile[upgrade.field]+1)-profile.gold }} vàng</small></div>
        <button class="war-button small" :aria-label="profile[upgrade.field]>=5?`${upgrade.name}: đã tối đa`:`${upgrade.name} lên cấp ${profile[upgrade.field]+1}, ${upgrade.base*(profile[upgrade.field]+1)} vàng`" :disabled="busy || profile[upgrade.field] >= 5 || profile.gold < upgrade.base*(profile[upgrade.field]+1)" @click="$emit('upgrade',upgrade.id)"><span>{{ profile[upgrade.field]>=5?'Đã tối đa':'Nâng cấp' }}</span><small v-if="profile[upgrade.field]<5">◈ {{ upgrade.base*(profile[upgrade.field]+1) }}</small></button>
      </div>
    </div>
    <p class="war-muted camp-footnote">Nâng cấp áp dụng từ trận chiến dịch tiếp theo. Đấu tướng PvP giữ chỉ số ngang nhau.</p>
  </section>
</template>
<style scoped>
.camp-head { display:flex;align-items:center;justify-content:space-between;gap:1rem; }.camp-head h2 { margin:.3rem 0; }.level-seal { border:1px solid #c3a261;border-radius:50%;width:54px;height:54px;display:flex;align-items:center;justify-content:center;flex-direction:column;font-family:Georgia,serif;color:#edcf87;font-size:1.2rem; }.level-seal small {font: .5rem sans-serif;letter-spacing:.15em;}
.camp-resources { display:flex;flex-wrap:wrap;gap:.8rem;margin:1rem 0 .6rem;font-size:.85rem; }.camp-resources strong {color:#f0ce79;}.camp-resources small {font-weight:400;}.camp-resources span {color:#d2bfd9;}.experience-bar {width:100%;height:5px;accent-color:#bd94c9;}.camp-summary {font-size:.8rem;color:#baa88d;}
.upgrade-list {display:flex;flex-direction:column;gap:.6rem;margin-top:1rem;}.upgrade-row {display:flex;align-items:center;gap:.65rem;padding:.8rem 0;border-top:1px solid #c6ac7726;}.upgrade-icon {font-size:1.6rem;color:#d8bb7a;min-width:30px;text-align:center;}.upgrade-description {flex:1;display:flex;flex-direction:column;gap:.3rem;font-size:.82rem;}.upgrade-description span {font-size:.72rem;color:#b3a38c;}.upgrade-description small {font-weight:400;color:#d8bb7a;}.camp-footnote {font-size:.7rem;line-height:1.6;margin-bottom:0;}
.experience-label{display:flex;justify-content:space-between;gap:8px;color:#c7bba4;font-size:.69rem;line-height:1.5;margin-bottom:6px}.experience-label small{font-size:inherit;white-space:nowrap;color:#b7a8c5}.experience-bar{display:block;height:6px;border:0;border-radius:8px;overflow:hidden;background:#33373a}.experience-bar::-webkit-progress-bar{background:#33373a}.experience-bar::-webkit-progress-value{background:linear-gradient(90deg,#927eaa,#d7bce5);border-radius:8px}.experience-bar::-moz-progress-bar{background:#bd94c9}.camp-stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px;margin:18px 0 10px}.camp-stats>div{padding:10px 7px;background:#111a1c88;border:1px solid #bda26620;border-radius:8px;text-align:center}.camp-stats dt{font-size:.62rem;color:#b8aa90;line-height:1.5}.camp-stats dd{font-family:Georgia,serif;color:#e8cf96;font-size:1.25rem;margin:4px 0 0}.camp-summary{line-height:1.65;font-size:.7rem}.upgrade-description{min-width:0}.upgrade-description strong{display:flex;align-items:center;justify-content:space-between;gap:6px}.upgrade-description>span{line-height:1.5}.upgrade-pips{display:flex;gap:4px;margin-top:2px}.upgrade-pips i{width:18px;height:3px;border-radius:3px;background:#53606466}.upgrade-pips i.filled{background:#d6bb7e}.upgrade-description .upgrade-missing{font-size:.63rem;color:#cba58b}.upgrade-row .war-button{display:flex;flex-direction:column;gap:4px;align-items:center;min-width:78px;padding:9px 10px}.upgrade-row .war-button small{font-weight:500;font-size:.66rem}.upgrade-row .war-button:disabled{opacity:.6}
@media(max-width:380px){.upgrade-row{gap:8px}.upgrade-icon{min-width:22px;font-size:1.3rem}.upgrade-description{font-size:.76rem}.upgrade-row .war-button{min-width:68px;padding:9px 7px}}
</style>
