<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getLeaderboard } from '@/api/rooms'
import { useStompClient } from '@/composables/useStompClient'
import type { GameTypeCode, LeaderboardEntry } from '@/types/game'

const props = defineProps<{ gameType: GameTypeCode }>()
const entries = ref<LeaderboardEntry[]>([])
const stomp = useStompClient()
let unsubscribe: (() => void) | undefined

onMounted(async () => {
  try { entries.value = await getLeaderboard(props.gameType) } catch { /* optional HUD */ }
  try {
    await stomp.connect()
    unsubscribe = stomp.subscribe(`/topic/leaderboard/${props.gameType}`, (value: LeaderboardEntry[]) => {
      entries.value = value
    })
  } catch { /* game connection reports its own errors */ }
})

onUnmounted(() => unsubscribe?.())
</script>

<template>
  <aside class="global-leaderboard">
    <div class="rank-title"><span>🏆 HÔM NAY</span><i>LIVE</i></div>
    <div v-if="entries.length" class="rank-list">
      <div v-for="(entry, index) in entries.slice(0, 5)" :key="entry.playerId" class="rank-item">
        <b>{{ index + 1 }}</b><span>{{ entry.username }}</span><strong>{{ entry.score }}</strong>
      </div>
    </div>
    <p v-else>Chưa có điểm</p>
  </aside>
</template>

<style scoped>
.global-leaderboard { width: 210px; padding: .8rem; border-radius: 14px; color: #fff; background: rgba(6,10,24,.78); border: 1px solid rgba(103,232,249,.18); backdrop-filter: blur(12px); box-shadow: 0 12px 30px rgba(0,0,0,.3); }
.rank-title { display: flex; justify-content: space-between; align-items: center; padding-bottom: .55rem; border-bottom: 1px solid rgba(255,255,255,.1); font-size: .72rem; font-weight: 800; letter-spacing: .08em; }
.rank-title i { color: #4ade80; font-size: .6rem; font-style: normal; }
.rank-list { display: grid; gap: .25rem; margin-top: .45rem; }
.rank-item { display: grid; grid-template-columns: 20px 1fr auto; gap: .35rem; align-items: center; padding: .34rem .4rem; border-radius: 7px; background: rgba(255,255,255,.04); font-size: .72rem; }
.rank-item b { color: #67e8f9; }
.rank-item span { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.rank-item strong { color: #fbbf24; }
p { margin: .65rem 0 0; text-align: center; color: rgba(255,255,255,.45); font-size: .7rem; }
</style>
