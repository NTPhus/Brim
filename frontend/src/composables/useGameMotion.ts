import { onUnmounted } from 'vue'

/** Decorative motion never delays a command or changes an authoritative snapshot. */
export function useGameMotion() {
  const preference=window.matchMedia('(prefers-reduced-motion: reduce)')
  const active=new Map<Element,Animation>()
  const ghosts=new Set<Element>()
  let disposed=false
  function cancel() {
    active.forEach(animation=>animation.cancel());active.clear()
    ghosts.forEach(element=>element.remove());ghosts.clear()
  }
  function animate(element:Element|null|undefined,frames:Keyframe[],duration=220) {
    if(!element||disposed||preference.matches)return Promise.resolve()
    active.get(element)?.cancel()
    const animation=element.animate(frames,{duration,easing:'cubic-bezier(.2,.75,.3,1)'})
    active.set(element,animation)
    return animation.finished.catch(()=>{}).then(()=>{if(active.get(element)===animation)active.delete(element)})
  }
  function vanish(element:HTMLElement,rect:DOMRect) {
    if(disposed||preference.matches)return
    const ghost=element.cloneNode(true) as HTMLElement
    ghost.removeAttribute('id');ghost.removeAttribute('data-motion-key')
    ghost.setAttribute('aria-hidden','true');ghost.inert=true
    // Keep scoped styles and inherited typography while removing the clone from layout.
    Object.assign(ghost.style,{position:'fixed',left:`${rect.left}px`,top:`${rect.top}px`,width:`${rect.width}px`,height:`${rect.height}px`,margin:'0',pointerEvents:'none',zIndex:'60'})
    element.parentElement?.appendChild(ghost);ghosts.add(ghost)
    void animate(ghost,[{opacity:.85,transform:'scale(1)'},{opacity:0,transform:'scale(.5)'}],160).then(()=>{ghost.remove();ghosts.delete(ghost)})
  }
  const changed=()=>{if(preference.matches)cancel()}
  preference.addEventListener('change',changed)
  onUnmounted(()=>{disposed=true;cancel();preference.removeEventListener('change',changed)})
  return {animate,vanish,cancel,reduced:()=>preference.matches}
}
