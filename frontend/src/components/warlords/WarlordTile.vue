<script setup lang="ts">
defineProps<{ value: number }>()
</script>
<template>
  <svg viewBox="0 0 48 48" class="resource-icon" aria-hidden="true" focusable="false">
    <g v-if="value === 1" stroke-linejoin="round">
      <path d="M33 5 42 6 41 15 21 35 13 27Z" fill="#f4e5c6" stroke="#7a351d" stroke-width="2"/>
      <path d="m34 12-17 19" stroke="#c4a279" stroke-width="3"/>
      <path d="m12 25 12 12-4 4L8 29Z" fill="#e7b54b" stroke="#75482b" stroke-width="2"/>
      <path d="m12 35-7 7" stroke="#5b3529" stroke-width="6"/><path d="m4 42 3 3" stroke="#f6cf6e" stroke-width="4"/>
    </g>
    <g v-else-if="value === 2"><path d="M28 3 10 27h12l-2 18 19-28H27Z" fill="#b4f0ff" stroke="#276e91" stroke-width="2"/><path d="m27 13-9 12h9l-2 9 9-14h-9Z" fill="#fff"/></g>
    <g v-else-if="value === 3"><path d="M24 41C4 29 3 17 9 10c5-6 12-3 15 2 4-5 11-8 16-2 7 9 1 20-16 31Z" fill="#fda6a0" stroke="#8c3e4b" stroke-width="2"/><path d="M12 16c1-3 4-4 6-2" fill="none" stroke="#ffe9d9" stroke-width="4" stroke-linecap="round"/><path d="M14 26h6l3-7 3 13 3-6h6" fill="none" stroke="#fff4de" stroke-width="2"/></g>
    <g v-else-if="value === 4"><path d="m24 4 16 7-2 18c-3 8-9 12-14 15-7-4-12-9-14-15L8 11Z" fill="#bedaa8" stroke="#47694c" stroke-width="2"/><path d="m24 10 10 5-2 12c-1 5-5 9-8 11-4-3-7-7-8-11l-2-12Z" fill="#719e78"/><path d="M24 13v21m-7-13h14" stroke="#ecf0bd" stroke-width="3"/></g>
    <g v-else-if="value === 5"><circle cx="24" cy="24" r="18" fill="#f5cc60" stroke="#8e6528" stroke-width="3"/><circle cx="24" cy="24" r="13" fill="none" stroke="#ffe9a1" stroke-width="2"/><path d="M20 20h8v8h-8Z" fill="#86612f" stroke="#ab802d" stroke-width="2"/><path d="m14 14 3-2m14 24 3-2" stroke="#fff4c6" stroke-width="3" stroke-linecap="round"/></g>
    <g v-else-if="value === 6"><path d="M12 8h25l-3 31H10l3-25" fill="#e6c7ef" stroke="#72518e" stroke-width="2"/><path d="M12 8c-8-1-9 8-3 9h6m18 15c-9-2-10 8-3 8h4c7-1 6-8-1-8Z" fill="#b58ac5" stroke="#72518e" stroke-width="2"/><path d="m25 13 2 5 6 1-5 4 1 6-5-3-5 3 1-6-4-4 6-1Z" fill="#fff3ce" stroke="#9b7bb1"/></g>
  </svg>
</template>
<style scoped>
.resource-icon { width: 85%; height: 85%; display: block; filter: drop-shadow(0 2px 1px #0005); }
</style>
