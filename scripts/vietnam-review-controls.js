// Browser-side keyboard automation. Uses real UI handlers; never changes game state.
(() => {
 const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const held=new Set();
 function key(code,down){if(held.has(code)===down)return;down?held.add(code):held.delete(code);document.body.dispatchEvent(new KeyboardEvent(down?'keydown':'keyup',{code,bubbles:true}));}
 function stop(){for(const code of [...held])key(code,false)}
 window.reviewAction=async action=>{const code={INTERACT:'KeyE',RELOAD:'KeyR',REPAIR:'KeyF'}[action];key(code,true);key(code,false);await sleep(200)};
 window.reviewWalk=async ([x,y])=>{
  stop();const until=Date.now()+90000;
  while(Date.now()<until){
   const s=reviewState,a=s.units[0],dist=Math.hypot(x-a.x,y-a.y);
   if(s.status==='FINISHED'){stop();return {status:s.status,objective:s.objective,hp:a.hp}}
   if(a.tank){
    const speed=a.tacticalView.speed;
    if(dist<20&&Math.abs(speed)<.6){stop();return {x:a.x,y:a.y,objective:s.objective}}
    let diff=Math.atan2(y-a.y,x-a.x)-a.hullAngle;while(diff>Math.PI)diff-=2*Math.PI;while(diff<-Math.PI)diff+=2*Math.PI;
    key('KeyA',diff<-.045);key('KeyD',diff>.045);
    key('KeyW',Math.abs(diff)<.15&&dist>Math.max(8,speed*speed/70+4));
   }else{
    if(Math.abs(x-a.x)<13&&Math.abs(y-a.y)<13){stop();return {x:a.x,y:a.y,objective:s.objective,hp:a.hp}}
    key('KeyA',x-a.x < -12);key('KeyD',x-a.x>12);key('KeyW',y-a.y < -12);key('KeyS',y-a.y>12);
   }
   await sleep(100);
  }
  stop();throw Error('Route timed out '+JSON.stringify(reviewState.units[0]));
 };
 window.reviewFight=async seconds=>{
  stop();const until=Date.now()+seconds*1000;
  while(Date.now()<until&&reviewState.status!=='FINISHED'){
   const s=reviewState,a=s.units[0],targets=s.units.filter(u=>!u.ally&&u.hp>0).sort((u,v)=>Math.hypot(u.x-a.x,u.y-a.y)-Math.hypot(v.x-a.x,v.y-a.y));
   if(!targets.length){stop();return 'No visible enemies'}
   const t=targets[0],canvas=document.querySelector('canvas'),rect=canvas.getBoundingClientRect();
   const scale=Math.max(.65,Math.min(1.2,rect.width/1000,rect.height/650));
   const cx=Math.max(0,Math.min(1600-rect.width/scale,a.x-rect.width/scale/2)),cy=Math.max(0,Math.min(1000-rect.height/scale,a.y-rect.height/scale/2));
   canvas.dispatchEvent(new PointerEvent('pointermove',{clientX:rect.left+(t.x-cx)*scale,clientY:rect.top+(t.y-cy)*scale,bubbles:true}));
   key('KeyC',true);key('Space',s.ammo>0);if(s.ammo===0&&!s.reload)await reviewAction('RELOAD');
   await sleep(100);
  }
  stop();return {hp:reviewState.units[0].hp,enemies:reviewState.units.filter(u=>!u.ally&&u.hp>0).length,ammo:reviewState.ammo};
 };
 return 'UI keyboard controls installed';
})()
