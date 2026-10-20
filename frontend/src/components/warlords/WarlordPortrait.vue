<script setup lang="ts">
import type { WarlordsHero } from '@/types/warlords'
defineProps<{ hero?: WarlordsHero | null; side?: number }>()
</script>
<template>
  <div class="general-portrait" :style="{color:hero?.color??(side===1?'#698da7':'#bf9652')}" aria-hidden="true">
    <svg viewBox="0 0 100 110">
      <path d="M9 110 18 82 38 70h24l20 12 9 28" fill="#263440"/>
      <path d="m18 83 22-12 10 34 10-34 22 12-7 27H25Z" fill="currentColor"/>
      <path d="M32 39h36v19c-2 14-11 20-18 20S33 71 32 58Z" fill="#c9a17e"/>
      <path v-if="hero?.emblem==='lotus'" d="M29 54V29Q50 7 71 29v25l-9-17-12-6-12 6Z" fill="#302532"/>
      <path v-else d="m30 42 5-21 15-9 15 9 5 21-20-7Z" fill="currentColor" stroke="#e4c688" stroke-width="2"/>
      <path v-if="hero?.emblem==='spear'" d="M49 5q25 1 19 23L51 35Z" fill="#dce6e5"/>
      <path v-else-if="hero?.emblem==='scroll'" d="m25 30 25-18 25 18-7 8H32Z" fill="#443757" stroke="#d6bd90" stroke-width="2"/>
      <path v-else-if="hero?.emblem==='lotus'" d="m34 29 5-14 11 12 11-12 5 14-16 6Z" fill="#efd39b"/>
      <path v-else d="M50 4v30M25 42l25-7 25 7" stroke="#dfc187" stroke-width="5" stroke-linecap="round"/>
      <path d="m37 52 7 1m12 0 7-1" stroke="#483729" stroke-width="3"/>
      <path v-if="hero?.emblem!=='lotus'" d="m43 65 7 9 7-9" fill="#47382e"/>
      <path v-else d="m43 66 7 2 7-2" fill="none" stroke="#a45862" stroke-width="2"/>
      <path d="m30 87 20 13 20-13M25 101h12m26 0h12" fill="none" stroke="#e2c38a" stroke-width="2"/>
      <path v-if="hero?.emblem==='shield'" d="M37 82h26v15l-13 11-13-11Z" fill="#314b49" stroke="#e6d1a5" stroke-width="2"/>
      <path v-if="hero?.emblem==='sword'" d="m72 75-5 30m-6-11 15 3" stroke="#f2e2bc" stroke-width="4"/>
      <path v-if="hero?.emblem==='banner'" d="M15 21v56m0-54h15l-6 12H15" fill="currentColor" stroke="#e3c58a" stroke-width="2"/>
    </svg>
  </div>
</template>
<style scoped>
.general-portrait{width:65px;height:75px;flex-shrink:0;border-radius:10px;background:radial-gradient(ellipse,#8c61404a,#11191e);overflow:hidden}.general-portrait svg{width:100%;height:100%}
</style>
