import { nextTick } from 'vue'
import type { useGameMotion } from '@/composables/useGameMotion'

interface PuzzleFrame { type:string; cells:Array<{row:number;column:number}>; board:number[][] }
/** Gravity is resolved separately inside each segment between immovable blocks. */
export async function animatePuzzleFrame(
  root:HTMLElement|null,before:number[][],frame:PuzzleFrame,
  motion:ReturnType<typeof useGameMotion>,setBoard:()=>void,valid:()=>boolean
) {
  const tile=(r:number,c:number)=>root?.children[r*8+c]?.querySelector('.motion-piece,svg')
  if(frame.type==='CLEAR'){
    await Promise.all(frame.cells.map(cell=>motion.animate(tile(cell.row,cell.column),[{opacity:1,transform:'scale(1)'},{opacity:0,transform:'scale(.25)'}],150)))
    if(!valid())return
  }
  const offsets:Array<{r:number;c:number;dx:number;dy:number}>=[]
  if(frame.type==='SWAP'&&frame.cells.length===2){
    const [a,b]=frame.cells
    offsets.push({r:a.row,c:a.column,dx:b.column-a.column,dy:b.row-a.row},{r:b.row,c:b.column,dx:a.column-b.column,dy:a.row-b.row})
  }
  if(frame.type==='FALL')for(let c=0;c<8;c++){
    let bottom=7
    while(bottom>=0){
      if(before[bottom]?.[c]===-1){bottom--;continue}
      let top=bottom
      while(top>0&&before[top-1]?.[c]!==-1)top--
      const survivors=[]
      for(let r=top;r<=bottom;r++)if(before[r]?.[c]>0)survivors.push(r)
      const added=bottom-top+1-survivors.length
      for(let r=top;r<=bottom;r++){
        const origin=r<top+added?r-added:survivors[r-top-added]!
        if(origin!==r)offsets.push({r,c,dx:0,dy:origin-r})
      }
      bottom=top-1
    }
  }
  setBoard();await nextTick()
  if(!valid())return
  const first=root?.children[0]?.getBoundingClientRect(),right=root?.children[1]?.getBoundingClientRect(),below=root?.children[8]?.getBoundingClientRect()
  const x=first&&right?right.left-first.left:0,y=first&&below?below.top-first.top:0
  await Promise.all(offsets.map(offset=>motion.animate(tile(offset.r,offset.c),[
    {transform:`translate(${offset.dx*x}px,${offset.dy*y}px)`},
    {transform:'translate(0,0)'}
  ],frame.type==='SWAP'?200:270)))
  if(frame.type==='RESHUFFLE')await Promise.all([...root?.querySelectorAll('.motion-piece,svg')??[]].map(el=>motion.animate(el,[{opacity:.2,transform:'scale(.8)'},{opacity:1,transform:'scale(1)'}],200)))
}
