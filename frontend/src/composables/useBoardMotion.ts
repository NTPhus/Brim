import { nextTick, onUnmounted, watch, type Ref } from 'vue'
import { useGameMotion } from './useGameMotion'

export interface MotionToken {
  key:string
  kind:string
  position?:string
  from?:string
  effect?:'place'|'flip'|'drop'|'hit'|'miss'
}
interface MotionFrame { tokens:MotionToken[]; epoch?:string|number; sequence?:number }

/** Compare snapshots before Vue patches, then animate only the changed pieces. */
export function useBoardMotion(root:Ref<HTMLElement|null>,source:()=>MotionFrame|null) {
  const motion=useGameMotion()
  let generation=0
  const elements=()=>new Map([...root.value?.querySelectorAll<HTMLElement>('[data-motion-key]')??[]].map(el=>[el.dataset.motionKey!,el]))
  watch(source,async(next,previous)=>{
    const version=++generation
    if(!next||!previous||next.epoch!==previous.epoch){motion.cancel();return}
    if(next.sequence!==undefined&&next.sequence===previous.sequence)return
    if(next.sequence!==undefined&&next.sequence!==(previous.sequence??0)+1){motion.cancel();return}
    if(motion.reduced())return
    const oldElements=elements()
    const oldRects=new Map([...oldElements].map(([key,el])=>[key,el.getBoundingClientRect()]))
    const oldTokens=new Map(previous.tokens.map(token=>[token.key,token]))
    const newTokens=new Map(next.tokens.map(token=>[token.key,token]))
    const origins=new Set(next.tokens.map(token=>token.from).filter(Boolean))
    for(const old of previous.tokens){
      if(old.kind&&(!newTokens.get(old.key)?.kind||newTokens.get(old.key)?.kind!==old.kind)&&!origins.has(old.key)){
        const el=oldElements.get(old.key),rect=oldRects.get(old.key)
        if(el&&rect&&old.effect==='place')motion.vanish(el,rect)
      }
    }
    await nextTick()
    if(version!==generation)return
    const current=elements()
    for(const token of next.tokens){
      if(!token.kind)continue
      const previousToken=oldTokens.get(token.key)
      const moved=token.from&&token.from!==token.key&&oldTokens.get(token.from)?.kind
      if(!moved&&previousToken?.kind===token.kind&&previousToken.position===token.position)continue
      const el=current.get(token.key)
      if(!el)continue
      const rect=el.getBoundingClientRect()
      const origin=oldRects.get(moved?token.from!:token.key)
      if(origin&&(moved||previousToken?.kind===token.kind)){
        const dx=origin.left-rect.left,dy=origin.top-rect.top
        if(dx||dy)void motion.animate(el,[{transform:`translate(${dx}px,${dy}px)`,zIndex:4},{transform:'translate(0,0)',zIndex:4}],token.position?130:240)
      }else if(token.effect==='flip'){
        void motion.animate(el,[{transform:'perspective(400px) rotateY(-90deg)',filter:'brightness(.65)'},{transform:'perspective(400px) rotateY(0)',filter:'brightness(1)'}],300)
      }else if(token.effect==='drop'){
        const top=root.value!.getBoundingClientRect().top
        void motion.animate(el,[{transform:`translateY(${top-rect.top-rect.height}px)`},{transform:'translateY(5px)',offset:.82},{transform:'translateY(0)'}],Math.min(420,200+(rect.top-top)*.3))
      }else if(token.effect==='hit'){
        void motion.animate(el,[{boxShadow:'inset 0 0 0 5px #ffe3a5',filter:'brightness(1.8)'},{boxShadow:'inset 0 0 0 0px transparent',filter:'brightness(1)'}],360)
      }else if(token.effect==='miss'){
        void motion.animate(el,[{boxShadow:'inset 0 0 0 10px #8bdfff99'},{boxShadow:'inset 0 0 0 0px #8bdfff00'}],380)
      }else{
        void motion.animate(el,[{transform:'scale(.55)',opacity:.25},{transform:'scale(1.08)',opacity:1,offset:.75},{transform:'scale(1)',opacity:1}],200)
      }
    }
  },{flush:'pre'})
  onUnmounted(()=>{generation++})
  return motion
}
