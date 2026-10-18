<script setup lang="ts">
import { computed, ref } from 'vue'
import { useBoardSession } from '@/composables/useBoardSession'
import type { GridState } from '@/types/arcade'
import '@/game/arcade.css'
import { useBoardMotion } from '@/composables/useBoardMotion'
const props = defineProps<{ kind: 'CARO' | 'OTHELLO' }>()
const game = useBoardSession<GridState>(props.kind)
const { state, room, pending, error, myTurn, finished, result, connected } = game
const motionBoard=ref<HTMLElement|null>(null)
useBoardMotion(motionBoard,()=>state.value?{sequence:state.value.board.flat().filter(Boolean).length,tokens:state.value.board.flatMap((row,r)=>row.map((cell,c)=>({key:r+','+c,kind:cell?String(cell):'',effect:props.kind==='OTHELLO'?'flip' as const:'place' as const})))}:null)
const title = props.kind === 'CARO' ? '✕ Caro' : '◐ Othello'
const legal = computed(() => new Set(state.value?.legalMoves.map(m => `${m.row},${m.column}`)))
const wins = computed(() => new Set(state.value?.winningCells.map(m => `${m.row},${m.column}`)))
</script>
<template>
  <section class="arcade card" :class="{ 'game-finished':finished }">
    <header><h1>{{ title }}</h1><button class="secondary" @click="game.leave">Rời bàn</button></header>
    <p>{{ room?.name }}</p>
    <p role="status" aria-live="polite">{{ !connected ? 'Đang kết nối lại…' : finished ? result : myTurn ? 'Đến lượt bạn' : `Đợi ${game.name(state?.currentPlayer)}…` }}</p>
    <template v-if="state">
      <div class="arcade-players">
        <span :class="{ active: state.currentPlayer === state.player1 }">{{ kind === 'CARO' ? '✕' : '●' }} {{ game.name(state.player1) }} · {{ state.score1 }}</span>
        <span :class="{ active: state.currentPlayer === state.player2 }">{{ kind === 'CARO' ? '○' : '○' }} {{ game.name(state.player2) }} · {{ state.score2 }}</span>
      </div>
      <p v-if="state.passedPlayer">{{ game.name(state.passedPlayer) }} không có nước đi hợp lệ, tự động bỏ lượt.</p>
      <div ref="motionBoard" class="arcade-grid" :class="kind.toLowerCase()" :style="{ gridTemplateColumns: `repeat(${state.board.length}, minmax(0, 1fr))` }" :aria-label="title">
        <template v-for="(row, r) in state.board" :key="r">
          <button v-for="(cell, c) in row" :key="c" class="grid-cell" :class="{ one: cell === 1, two: cell === 2,
            legal: kind === 'OTHELLO' && legal.has(`${r},${c}`), winning: wins.has(`${r},${c}`), last: state.lastMove?.row === r && state.lastMove?.column === c }"
            :aria-label="`Hàng ${r+1}, cột ${c+1}: ${cell === 0 ? 'trống' : cell === 1 ? 'quân 1' : 'quân 2'}`"
            :disabled="!connected || !myTurn || pending || !legal.has(`${r},${c}`)" @click="game.move({ row: r, column: c })">
            <span class="motion-piece" :data-motion-key="`${r},${c}`">{{ cell === 0 ? (kind === 'OTHELLO' && legal.has(`${r},${c}`) ? '·' : '') : kind === 'CARO' ? (cell === 1 ? '✕' : '○') : (cell === 1 ? '●' : '○') }}</span>
          </button>
        </template>
      </div>
    </template>
    <p class="arcade-help">{{ kind === 'CARO' ? 'Bàn 15×15, X đi trước. Từ 5 quân liên tiếp ngang/dọc/chéo là thắng, kể cả bị chặn hai đầu (freestyle).' : 'Đen đi trước. Đặt quân tại dấu chấm để kẹp và lật quân đối thủ. Không có nước thì tự bỏ lượt; cả hai hết nước, bên nhiều quân hơn thắng.' }}</p>
    <p v-if="error" role="alert" class="arcade-error">{{ error }}</p>
    <button v-if="finished && room?.hostId === game.session.player.id" :disabled="!connected" @click="game.restart">Chơi ván mới</button>
  </section>
</template>
