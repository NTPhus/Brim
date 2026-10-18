<script setup lang="ts">
import { computed, ref } from 'vue'
import { useBoardSession } from '@/composables/useBoardSession'
import type { BattleshipState } from '@/types/arcade'
import '@/game/arcade.css'
import { useBoardMotion } from '@/composables/useBoardMotion'
const game = useBoardSession<BattleshipState>('BATTLESHIP')
const { state, room, pending, error, finished, myTurn, result, connected } = game
const motionBoard=ref<HTMLElement|null>(null)
useBoardMotion(motionBoard,()=>state.value?{tokens:[...state.value.ownBoard.flatMap((row,r)=>row.map((cell,c)=>({key:'own-'+r+','+c,kind:String(cell),effect:cell===3?'hit' as const:cell===2?'miss' as const:'place' as const}))),...state.value.targetBoard.flatMap((row,r)=>row.map((cell,c)=>({key:'target-'+r+','+c,kind:String(cell),effect:cell===3?'hit' as const:'miss' as const})))]}:null)
const horizontal = ref(true)
const aim = ref<{ row: number; column: number } | null>(null)
const placement = computed(() => state.value?.phase === 'PLACEMENT')
const preview = computed(() => {
  const cells = new Set<string>()
  if (!aim.value || !placement.value || !state.value?.nextShipLength) return cells
  for (let i=0;i<state.value.nextShipLength;i++) {
    const r=aim.value.row+(horizontal.value?0:i), c=aim.value.column+(horizontal.value?i:0)
    if (r>=10 || c>=10 || state.value.ownBoard[r][c]!==0) return new Set<string>()
    cells.add(`${r},${c}`)
  }
  return cells
})
function place(row: number, column: number) {
  if (!placement.value || !state.value?.nextShipLength) return
  aim.value = { row, column }
  if (!preview.value.size) { error.value='Tàu phải nằm trong bàn và không chồng tàu khác.'; return }
  game.move({ action: 'PLACE', row, column, horizontal: horizontal.value })
}
const symbol = (cell: number, own: boolean) => cell === 3 ? '✹' : cell === 2 ? '·' : cell === 1 && own ? '■' : ''
</script>
<template>
  <section class="arcade card battleship" :class="{ 'game-finished':finished }">
    <header><h1>🚢 Battleship</h1><button class="secondary" @click="game.leave">Rời bàn</button></header>
    <p role="status" aria-live="polite">{{ !connected ? 'Đang kết nối lại…' : finished ? result : placement ? 'Xếp hạm đội của bạn' : myTurn ? 'Đến lượt bạn bắn' : `Đợi ${game.name(state?.currentPlayer)} bắn…` }}</p>
    <template v-if="state">
      <div v-if="placement" class="arcade-actions">
        <button :disabled="pending || !state.nextShipLength || !connected" @click="horizontal = !horizontal">{{ horizontal ? '↔ Ngang' : '↕ Dọc' }} · Đổi hướng</button>
        <button :disabled="pending || !state.nextShipLength || !connected" @click="game.move({ action: 'AUTO_PLACE' })">Xếp tự động{{ state.placedShips ? ' (đặt lại)' : '' }}</button>
      </div>
      <p v-if="placement">{{ state.nextShipLength ? `Đặt tàu dài ${state.nextShipLength} ô (${state.placedShips}/5 đã xếp).` : 'Đã xếp đủ. Đợi đối thủ hoàn tất.' }} Đối thủ: {{ state.opponentPlacedShips }}/5.</p>
      <div ref="motionBoard" class="sea-boards">
        <div><h2>Hạm đội của bạn · {{ state.remainingOwn }} ô</h2>
          <div class="arcade-grid sea" @mouseleave="aim = null">
            <template v-for="(row, r) in state.ownBoard" :key="r">
              <button v-for="(cell, c) in row" :key="c" class="grid-cell" :data-motion-key="`own-${r},${c}`" :class="{ ship: cell === 1, hit: cell === 3, miss: cell === 2, preview: preview.has(`${r},${c}`) }"
                :disabled="!connected || !placement || !state.nextShipLength || pending" :aria-label="`Hàng ${r+1}, cột ${c+1}: ${['nước','tàu','trượt','trúng'][cell]}`"
                @mouseenter="aim = { row: r, column: c }" @focus="aim = { row: r, column: c }" @click="place(r,c)">{{ symbol(cell,true) }}</button>
            </template>
          </div>
        </div>
        <div><h2>Vùng bắn · {{ state.remainingOpponent }} ô tàu còn lại</h2>
          <div class="arcade-grid sea">
            <template v-for="(row,r) in state.targetBoard" :key="r">
              <button v-for="(cell,c) in row" :key="c" class="grid-cell" :data-motion-key="`target-${r},${c}`" :class="{ hit: cell === 3, miss: cell === 2 }"
                :disabled="!connected || placement || !myTurn || pending || cell !== 0" :aria-label="`Bắn hàng ${r+1}, cột ${c+1}: ${cell === 0 ? 'chưa bắn' : cell === 3 ? 'trúng' : 'trượt'}`"
                @click="game.move({ action: 'FIRE', row:r, column:c })">{{ symbol(cell,false) }}</button>
            </template>
          </div>
        </div>
      </div>
    </template>
    <p class="arcade-help">5 tàu dài 5, 4, 3, 3, 2 ô; được chạm nhau, không chồng lên nhau. Mỗi lượt bắn một ô, trúng cũng đổi lượt. Đánh chìm toàn bộ hạm đội đối thủ để thắng. ■ tàu của bạn · ✹ trúng · dấu chấm: trượt.</p>
    <p v-if="error" class="arcade-error" role="alert">{{ error }}</p>
    <button v-if="finished && room?.hostId === game.session.player.id" :disabled="!connected" @click="game.restart">Chơi ván mới</button>
  </section>
</template>
<style scoped>
.battleship { max-width: 1050px; }
.sea-boards { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
h2 { font-size: .9rem; min-height: 2em; }
.sea { grid-template-columns: repeat(10,minmax(0,1fr)); background: #16456b; }
.sea .grid-cell { background: #123453; font-size: clamp(.8rem,3vw,1.5rem); }
.sea .ship { background: #506888; }
.sea .hit { background: #923b45; color: #fff; }
.sea .miss { color: #fff; font-size: 2rem; }
.sea .preview { background: #3e8f79; outline: 1px solid #94f7c4; }
@media(max-width:650px) { .sea-boards { grid-template-columns: 1fr; } }
</style>
