<script setup lang="ts">
import { computed } from 'vue'
import type { WarlordsHero } from '@/types/warlords'
import WarlordPortrait from './WarlordPortrait.vue'
const props=defineProps<{ heroes:WarlordsHero[]; selected:string; busy?:boolean }>()
defineEmits<{select:[id:string]}>()
const chosen=computed(()=>props.heroes.find(hero=>hero.id===props.selected))
function bonuses(hero:WarlordsHero){
  const result=[]
  if(hero.hpBonus)result.push(`${hero.hpBonus>0?'+':''}${hero.hpBonus} HP`)
  if(hero.attackBonus)result.push(`${hero.attackBonus>0?'+':''}${hero.attackBonus} công`)
  if(hero.skillBonus)result.push(`+${hero.skillBonus} binh pháp`)
  if(hero.startingEnergy!==10)result.push(`${hero.startingEnergy} năng lượng đầu trận`)
  if(hero.startingShield)result.push(`${hero.startingShield} khiên đầu trận`)
  return result.join(' · ')||'140 HP · 5 công · 10 năng lượng cơ bản'
}
</script>
<template>
  <section class="war-panel hero-selection" aria-label="Chọn tướng xuất quân">
    <div class="hero-selection-heading"><div><span class="war-eyebrow">TƯỚNG XUẤT QUÂN</span><h2>Chọn người dẫn quân</h2></div><span class="hero-free">{{ heroes.length }} tướng · Miễn phí</span></div>
    <div class="hero-roster">
      <button v-for="hero in heroes" :key="hero.id" type="button" class="hero-option" :class="{chosen:selected===hero.id}" :style="{'--hero-color':hero.color}" :disabled="busy" :aria-pressed="selected===hero.id" @click="$emit('select',hero.id)">
        <WarlordPortrait :hero="hero"/><span><small>{{hero.title}}</small><strong>{{hero.name}}</strong></span><b v-if="selected===hero.id" aria-label="Đang chọn">✓</b>
      </button>
    </div>
    <div v-if="chosen" class="hero-description" aria-live="polite"><span class="selected-hero-label">✓ Đang dẫn quân</span><strong>{{chosen.name}} <small>· {{chosen.title}}</small></strong><p>{{chosen.description}}</p><span class="hero-bonuses">{{bonuses(chosen)}}</span></div>
    <p class="hero-note">Tất cả tướng đều chọn miễn phí. Chỉ số riêng áp dụng từ trận chiến dịch tiếp theo. PvP giữ chỉ số bằng nhau, dùng ngoại hình tướng đã chọn.</p>
  </section>
</template>
<style scoped>
.hero-selection h2{margin:.5rem 0 1rem}.hero-roster{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.hero-option{position:relative;display:flex;align-items:center;gap:8px;padding:9px;border:1px solid #a88b5140;border-radius:10px;background:#17232a;color:#e9d9ba;text-align:left;cursor:pointer;min-width:0}.hero-option.chosen{border-color:var(--hero-color);background:linear-gradient(135deg,#3e3d30,#18242a);box-shadow:inset 0 0 0 1px var(--hero-color)}.hero-option:focus-visible{outline:3px solid #f1d89f;outline-offset:3px}.hero-option:disabled{cursor:wait;opacity:.7}.hero-option span{display:flex;flex-direction:column;gap:5px;min-width:0}.hero-option strong{font-size:.78rem}.hero-option small{font-size:.65rem;color:var(--hero-color)}.hero-option b{position:absolute;right:5px;top:3px;color:#ead49c}.hero-option :deep(.general-portrait){width:42px;height:50px}.hero-description{padding:12px 0 0;font-size:.8rem}.hero-description strong{color:#e3c487}.hero-description p{color:#c3b69c;line-height:1.6;margin:.4rem 0}.hero-description>span{color:#9ac4b2;font-size:.72rem}.hero-note{font-size:.7rem;line-height:1.6;color:#ad9f87;margin-bottom:0}
.hero-selection-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.hero-free{font-size:.63rem;border:1px solid #bd9a5d35;background:#161f21;padding:6px 8px;border-radius:30px;white-space:nowrap;color:#d1bb8a}.hero-option{min-height:76px;transition:border-color .15s,background .15s}.hero-option:enabled:hover{background:#30352e;border-color:#d2b981}.hero-option strong{line-height:1.45;padding-right:4px}.hero-option small{font-size:.6rem;letter-spacing:.03em}.hero-option.chosen::after{content:'';position:absolute;inset:auto 15% 0;height:2px;background:var(--hero-color)}.hero-description{display:flex;flex-direction:column;gap:5px;padding:14px;margin-top:12px;border:1px solid #a8b88b25;border-radius:9px;background:#12221e66}.hero-description .selected-hero-label{font-size:.57rem;letter-spacing:.08em;color:#b2c49a}.hero-description strong{line-height:1.6}.hero-description strong small{font-size:.73rem;font-weight:400;color:#c2b393}.hero-description p{margin:0}.hero-description .hero-bonuses{display:block;line-height:1.65;color:#b1d3c4}.hero-note{font-size:.67rem}
@media(max-width:650px){.hero-roster{grid-template-columns:repeat(2,minmax(0,1fr))}.hero-option{padding:9px 7px;gap:7px}.hero-option strong{font-size:.72rem}.hero-selection-heading{align-items:flex-start}.hero-free{font-size:.57rem;padding:5px 7px}.hero-selection h2{font-size:1.15rem}.hero-option :deep(.general-portrait){width:34px;height:45px}}
@media(min-width:651px){.warlords-hub .hero-roster{grid-template-columns:repeat(6,minmax(0,1fr))}.warlords-hub .hero-option{flex-direction:column;gap:10px;align-items:flex-start;padding:13px 10px}.warlords-hub .hero-option :deep(.general-portrait){width:46px;height:55px}.warlords-hub .hero-option strong{font-size:.75rem}}
</style>
