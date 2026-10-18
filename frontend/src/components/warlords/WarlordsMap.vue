<script setup lang="ts">
import type { WarlordsChapter, WarlordsProfile } from '@/types/warlords'
const props = defineProps<{ chapters: WarlordsChapter[]; profile: WarlordsProfile; selected: number; disabled?: boolean }>()
defineEmits<{ select: [chapter: number] }>()
const path = () => props.chapters.map(chapter => `${chapter.x},${chapter.y}`).join(' ')
</script>
<template>
  <div class="campaign-map" aria-label="Lộ trình 12 chương Sơn Hà">
    <svg class="map-art" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
      <defs><pattern id="map-grid" width="10" height="10" patternUnits="userSpaceOnUse"><path d="M10 0H0v10" fill="none" stroke="#886840" stroke-opacity=".12" stroke-width=".15"/></pattern></defs>
      <rect width="100" height="100" fill="url(#map-grid)"/>
      <path d="M63-5c-5 16-13 16-12 29s15 15 10 27 20 12 17 23 9 13 23 15" fill="none" stroke="#749b9a" stroke-opacity=".45" stroke-width="4"/>
      <path d="M52 23c-14 6-9 17-14 24S22 64 28 76" fill="none" stroke="#749b9a" stroke-opacity=".35" stroke-width="2"/>
      <path d="m2 35 6-12 6 12m-3-7 5-9 5 10M8 64l7-10 7 10m60-53 5-7 5 7M7 88l5-7 5 7" fill="#a69870" fill-opacity=".35" stroke="#817151" stroke-opacity=".35" stroke-width=".4"/>
      <polyline :points="path()" fill="none" stroke="#77512e" stroke-width=".6" stroke-dasharray="1 1" stroke-linecap="round"/>
    </svg>
    <div class="map-label"><span>SƠN HÀ · THẾ KỶ X</span><strong>Hành trình hợp cờ</strong></div>
    <div class="compass" aria-hidden="true">✥<small>BẮC</small></div>
    <button v-for="chapter in chapters" :key="chapter.id" class="map-node" :class="{ selected: chapter.id === selected, cleared: profile.cleared.includes(chapter.id), locked: chapter.id > profile.unlocked }"
      :style="{ left: `${chapter.x}%`, top: `${chapter.y}%` }" :disabled="disabled || chapter.id > profile.unlocked"
      :aria-label="`Chương ${chapter.id}: ${chapter.warlord}, ${chapter.location}${chapter.id > profile.unlocked ? ', chưa mở' : profile.cleared.includes(chapter.id) ? ', đã hoàn thành' : ''}`"
      :aria-pressed="chapter.id === selected" @click="$emit('select', chapter.id)">
      <span class="node-seal">{{ chapter.id > profile.unlocked ? '⌑' : profile.cleared.includes(chapter.id) ? '✓' : chapter.id }}</span>
      <span class="node-label">{{ chapter.location }}</span>
    </button>
    <div class="map-home">⚑ HOA LƯ</div>
  </div>
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
</style>
