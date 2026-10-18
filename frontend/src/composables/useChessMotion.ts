import type { Ref } from 'vue'
import { useBoardMotion } from './useBoardMotion'

interface ChessSnapshot {
  pieces:Record<string,{type:string;color:string;x:number;y:number}>
  moveHistory:string[]
  lastMove?:{fromX:number;fromY:number;toX:number;toY:number}|null
}
export function useChessMotion(root:Ref<HTMLElement|null>,state:Ref<ChessSnapshot|null>,castling=false){
  return useBoardMotion(root,()=>{
    const snapshot=state.value
    if(!snapshot)return null
    const move=snapshot.lastMove
    const castle=castling&&move&&Math.abs(move.toX-move.fromX)===2&&snapshot.pieces[`${move.toX},${move.toY}`]?.type==='KING'
    return {sequence:snapshot.moveHistory.length,tokens:Object.values(snapshot.pieces).map(piece=>{
      const key=`${piece.x},${piece.y}`
      let from=move&&piece.x===move.toX&&piece.y===move.toY?`${move.fromX},${move.fromY}`:undefined
      if(castle&&piece.type==='ROOK'&&piece.y===move!.toY&&piece.x===(move!.toX>move!.fromX?5:3))from=`${move!.toX>move!.fromX?7:0},${piece.y}`
      return {key,kind:`${piece.color}:${piece.type}`,from,effect:'place' as const}
    })}
  })
}
