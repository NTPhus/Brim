import type Phaser from 'phaser'

/** Short, bounded world-space effects for the real-time renderers. */
export class WorldEffects {
  private particles:Array<{x:number;y:number;color:number;age:number;radius:number}>=[]
  private preference=window.matchMedia('(prefers-reduced-motion: reduce)')
  emit(x:number,y:number,color:number,radius=30){
    if(this.preference.matches)return
    if(this.particles.length>=48)this.particles.shift()
    this.particles.push({x,y,color,age:0,radius})
  }
  clear(){this.particles=[]}
  draw(graphics:Phaser.GameObjects.Graphics,delta:number){
    if(this.preference.matches){this.clear();return}
    for(const effect of this.particles){
      effect.age+=Math.min(delta,100)
      const progress=Math.min(1,effect.age/420)
      const radius=5+effect.radius*progress
      graphics.lineStyle(3*(1-progress)+.5,effect.color,(1-progress)*.8)
      graphics.strokeCircle(effect.x,effect.y,radius)
      graphics.fillStyle(effect.color,(1-progress)*.18)
      graphics.fillCircle(effect.x,effect.y,radius*.7)
    }
    this.particles=this.particles.filter(effect=>effect.age<420)
  }
}
