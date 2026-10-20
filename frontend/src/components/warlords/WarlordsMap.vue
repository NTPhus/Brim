<script setup lang="ts">
import type { WarlordsChapter, WarlordsProfile } from '@/types/warlords'
const props = defineProps<{ chapters: WarlordsChapter[]; profile: WarlordsProfile; selected: number; disabled?: boolean }>()
defineEmits<{ select: [chapter: number] }>()
const path = () => props.chapters.map(chapter => `${chapter.x},${chapter.y}`).join(' ')
const openPath = () => props.chapters.filter(chapter => chapter.id <= props.profile.unlocked).map(chapter => `${chapter.x},${chapter.y}`).join(' ')
const status = (id: number) => id > props.profile.unlocked ? 'Chưa mở' : props.profile.cleared.includes(id) ? 'Đã hoàn thành' : id === props.profile.unlocked ? 'Chương hiện tại' : 'Có thể chơi'
</script>
<template>
  <section class="campaign-route" aria-label="Lộ trình chiến dịch Sơn Hà">
  <div class="route-heading"><div><span class="war-eyebrow">CHIẾN DỊCH · {{ chapters.length }} CHƯƠNG</span><h2>Hành trình hợp cờ</h2></div><span class="route-progress">{{ profile.cleared.length }}<small> / {{ chapters.length }} hoàn thành</small></span></div>
  <div class="campaign-map" :aria-label="`Bản đồ lộ trình ${chapters.length} chương Sơn Hà`">
    <svg class="map-art" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
      <defs><pattern id="map-grid" width="10" height="10" patternUnits="userSpaceOnUse"><path d="M10 0H0v10" fill="none" stroke="#886840" stroke-opacity=".12" stroke-width=".15"/></pattern></defs>
      <rect width="100" height="100" fill="url(#map-grid)"/>
      <path d="M63-5c-5 16-13 16-12 29s15 15 10 27 20 12 17 23 9 13 23 15" fill="none" stroke="#749b9a" stroke-opacity=".45" stroke-width="4"/>
      <path d="M52 23c-14 6-9 17-14 24S22 64 28 76" fill="none" stroke="#749b9a" stroke-opacity=".35" stroke-width="2"/>
      <path d="m2 35 6-12 6 12m-3-7 5-9 5 10M8 64l7-10 7 10m60-53 5-7 5 7M7 88l5-7 5 7" fill="#a69870" fill-opacity=".35" stroke="#817151" stroke-opacity=".35" stroke-width=".4"/>
      <polyline :points="path()" fill="none" stroke="#77512e" stroke-width=".6" stroke-dasharray="1 1" stroke-linecap="round"/>
      <polyline :points="openPath()" fill="none" stroke="#8f392c" stroke-width=".8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <div class="map-label"><span>SƠN HÀ · THẾ KỶ X</span><strong>Từ ngọn cờ Hoa Lư</strong></div>
    <div class="compass" aria-hidden="true">✥<small>BẮC</small></div>
    <button v-for="chapter in chapters" :key="chapter.id" type="button" class="map-node" :class="{ selected: chapter.id === selected, cleared: profile.cleared.includes(chapter.id), current:chapter.id===profile.unlocked&&!profile.cleared.includes(chapter.id), locked: chapter.id > profile.unlocked }"
      :style="{ left: `${chapter.x}%`, top: `${chapter.y}%` }" :disabled="disabled || chapter.id > profile.unlocked"
      :aria-label="`Chương ${chapter.id}: ${chapter.warlord}, ${chapter.location}, ${status(chapter.id)}`" :title="`Chương ${chapter.id} · ${chapter.title} · ${status(chapter.id)}`"
      :aria-pressed="chapter.id === selected" @click="$emit('select', chapter.id)">
      <span class="node-seal">{{ chapter.id > profile.unlocked ? '⌑' : profile.cleared.includes(chapter.id) ? '✓' : chapter.id }}</span><small v-if="chapter.id === selected" class="node-selected">ĐANG CHỌN</small>
      <span class="node-label">{{ chapter.location }}</span>
    </button>
    <div class="map-home">⚑ HOA LƯ</div>
  </div>
  <div class="map-legend" aria-label="Chú giải bản đồ"><span><i class="legend-current"/>Đang chọn</span><span><i class="legend-cleared">✓</i>Đã qua</span><span><i class="legend-locked">⌑</i>Chưa mở</span></div>
  <details class="route-index"><summary>Chọn chương theo danh sách <span>{{ profile.unlocked }} / {{ chapters.length }} đã mở</span></summary><ol><li v-for="chapter in chapters" :key="chapter.id"><button type="button" :disabled="disabled||chapter.id>profile.unlocked" :class="{ selected:chapter.id===selected, cleared:profile.cleared.includes(chapter.id) }" :aria-pressed="chapter.id===selected" @click="$emit('select',chapter.id)"><b>{{ String(chapter.id).padStart(2,'0') }}</b><span><strong>{{ chapter.location }}</strong><small>{{ status(chapter.id) }}</small></span><i aria-hidden="true">{{ chapter.id>profile.unlocked?'⌑':profile.cleared.includes(chapter.id)?'✓':'→' }}</i></button></li></ol></details>
  <p class="route-footnote">Lộ trình minh họa theo chương, không thể hiện ranh giới lãnh thổ lịch sử.</p>
  </section>
</template>
<style scoped>
.campaign-map { position: relative; min-height: 620px; overflow: hidden; border: 1px solid #9a784c; border-radius: 18px; background: radial-gradient(ellipse at 20% 20%,#f3e4bb,#d6bb89 70%,#b89b6e); box-shadow: inset 0 0 65px #6b49294d; color: #4c3825; }
.map-art { position: absolute; inset: 0; width: 100%; height: 100%; }
.map-label { position: absolute; top: 3%; left: 5%; display: flex; flex-direction: column; gap: .3rem; pointer-events: none; }
.map-label span { font-size: .55rem; letter-spacing: .16em; }.map-label strong { font-family: Georgia,serif; font-size: 1.35rem; }
.compass { position: absolute; right: 4%; top: 4%; font-size: 2.5rem; text-align: center; color: #705537; }.compass small { display: block; font-size: .55rem; letter-spacing: .15em; }
.map-node { position: absolute; transform: translate(-50%,-50%); padding: 0; border: none; background: transparent; display: flex; flex-direction: column; align-items: center; gap: 4px; color: #382b20; min-width: 60px; z-index: 1; opacity: 1; }
.node-seal { display: grid; place-items: center; width: 35px; height: 35px; border-radius: 50%; border: 2px solid #ebce8f; outline: 2px solid #796040; background: #5d4530; color: #ffe8b4; box-shadow: 0 4px 10px #3a271e4d; font-family: Georgia,serif; font-weight: 700; font-size: 1.05rem; }
.node-label { font-size: .65rem; font-weight: 700; white-space: nowrap; background: #f0ddb0df; border-radius: 4px; padding: 2px 5px; }
.map-node:enabled:hover .node-seal,.map-node:focus-visible .node-seal,.map-node.selected .node-seal { background: #8e3b2e; outline: 3px solid #9d3d30; transform: scale(1.12); }
.map-node.cleared .node-seal { background: #496145; }.map-node.locked .node-seal { background: #a39378; border-color: #cabc9e; color: #e7d9bc; }
.map-node.locked .node-label { color: #817255; background: #dcc9a5b0; }.map-node:focus-visible { outline: 2px solid #842e26; outline-offset: 7px; border-radius: 5px; }
.map-home { position: absolute; bottom: 3%; right: 4%; font-family: Georgia,serif; font-weight: 700; letter-spacing: .12em; }
@media(max-width:480px) { .campaign-map { min-height: 570px; }.node-label { font-size: .57rem; }.node-seal { width:30px;height:30px; }.map-label strong { font-size: 1.05rem; } }
.campaign-route{min-width:0;border:1px solid #bd9a5d35;border-radius:17px;padding:18px;background:linear-gradient(155deg,#252b28,#182125)}.route-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:17px}.route-heading h2{margin:6px 0 0;font-size:1.35rem}.route-heading .war-eyebrow{font-size:.57rem}.route-progress{color:#e7ca8a;font-family:Georgia,serif;font-size:1.5rem;white-space:nowrap}.route-progress small{font-family:Arial,sans-serif;font-size:.62rem;color:#bdaf93}.campaign-map{min-height:590px;border-radius:8px;box-shadow:inset 0 0 65px #6b49294d,0 5px 18px #0003}.map-label{top:4%;left:6%}.map-label strong{font-size:1.05rem}.map-node{min-height:48px;min-width:48px;justify-content:center;cursor:pointer;touch-action:manipulation}.map-node:disabled{cursor:default}.node-seal{width:34px;height:34px;transition:transform .18s,background .18s;flex-shrink:0}.node-label{font-size:.6rem;line-height:1.4}.map-node.current .node-seal{background:#823e30;color:#ffedb9;outline-color:#94382c}.map-node.selected .node-seal{background:#8e3b2e;outline:3px solid #9d3d30;box-shadow:0 0 0 7px #8e3b2e24,0 4px 10px #3a271e4d;transform:scale(1.12)}.node-selected{position:absolute;bottom:calc(100% + 3px);font-size:.42rem;letter-spacing:.04em;white-space:nowrap;padding:3px 6px;background:#823e30;color:#ffedbe;border-radius:3px}.map-node.locked .node-seal{color:#f6ecd3;background:#94846b;border-color:#d6c4a1}.map-node.locked .node-label{color:#62583e;background:#e6d3b0c9}.map-home{font-size:.7rem}.map-legend{display:flex;justify-content:center;flex-wrap:wrap;gap:20px;padding:15px 0;font-size:.64rem;color:#c7b99e}.map-legend>span{display:flex;align-items:center;gap:6px}.map-legend i{display:grid;place-items:center;width:13px;height:13px;border-radius:50%;font-size:.55rem;font-style:normal}.legend-current{background:#9d4938;border:1px solid #e7c38b}.legend-cleared{background:#496145;color:#e7e9c0}.legend-locked{background:#7b7666;color:#e0dac8}.route-index{border-top:1px solid #bd9a5d25;padding-top:12px}.route-index summary{font-size:.72rem;color:#dec998;cursor:pointer;line-height:1.7}.route-index summary>span{float:right;font-size:.62rem;color:#afa38a}.route-index ol{padding:0;margin:12px 0 0;list-style:none;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.route-index li{min-width:0}.route-index button{display:flex;align-items:center;gap:9px;width:100%;min-height:57px;text-align:left;padding:8px;background:#182226;border:1px solid #b6a47730;border-radius:7px;color:#d4c4a3;cursor:pointer}.route-index button>span{min-width:0;display:flex;flex-direction:column;gap:3px}.route-index button strong{font-size:.68rem;font-weight:600}.route-index button small{font-size:.55rem;color:#b3a589}.route-index button b{font-family:Georgia,serif;font-size:1rem;color:#c9b07b}.route-index button i{font-style:normal;margin-left:auto;color:#c9b07b;font-size:.8rem}.route-index button.selected{border-color:#c9a365;background:#5c493032}.route-index button.cleared i{color:#a4c69d}.route-index button:disabled{opacity:.53;cursor:default}.route-index button:focus-visible{outline:2px solid #e9cf91;outline-offset:3px}.route-footnote{font-size:.6rem;color:#a99d86;line-height:1.6;margin:12px 0 0}
@media(max-width:480px){.campaign-route{padding:13px}.route-heading h2{font-size:1.15rem}.route-heading .war-eyebrow{font-size:.5rem;letter-spacing:.1em}.route-progress{font-size:1.25rem}.route-progress small{display:block;font-size:.53rem;text-align:right}.campaign-map{min-height:550px}.node-seal{width:30px;height:30px}.node-label{font-size:.54rem;padding:2px 3px}.map-label span{font-size:.45rem}.map-label strong{font-size:.9rem}.compass{font-size:1.9rem}.compass small{font-size:.43rem}.map-home{font-size:.6rem}.map-legend{gap:15px;font-size:.6rem}.route-index summary{font-size:.68rem}.route-index summary>span{font-size:.56rem}}
</style>
