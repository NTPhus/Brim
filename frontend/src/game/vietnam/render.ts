import type { CampaignState } from './types'
export interface Camera {x:number;y:number;scale:number}
export function renderBattle(canvas:HTMLCanvasElement,state:CampaignState,camera:Camera,time:number) {
  const ctx=canvas.getContext('2d');if(!ctx)return
  const w=canvas.clientWidth,h=canvas.clientHeight,dpr=Math.min(2,window.devicePixelRatio||1)
  if(canvas.width!==Math.round(w*dpr)||canvas.height!==Math.round(h*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr)}
  const me=state.units[0];if(!me)return
  camera.scale=Math.max(.65,Math.min(1.2,w/1000,h/650))
  const vw=w/camera.scale,vh=h/camera.scale
  camera.x=Math.max(0,Math.min(1600-vw,me.x-vw/2));camera.y=Math.max(0,Math.min(1000-vh,me.y-vh/2))
  ctx.setTransform(dpr,0,0,dpr,0,0);ctx.fillStyle='#101a15';ctx.fillRect(0,0,w,h)
  ctx.scale(camera.scale,camera.scale);ctx.translate(-camera.x,-camera.y)
  const underground=state.layer===1,theme=state.chapter.theme
  ctx.fillStyle=underground?'#12100c':theme==='HILLS'?'#74704a':theme==='JUNGLE'?'#3e5239':'#72806a';ctx.fillRect(0,0,1600,1000)
  if(!underground){
    for(let n=0;n<220;n++){
      const x=(n*137+43)%1600,y=(n*211+31)%1000
      ctx.fillStyle=n%2?'#ffffff08':'#0000000a';ctx.beginPath();ctx.ellipse(x,y,3+n%9,1+n%3,n,0,Math.PI*2);ctx.fill()
    }
    if(theme==='HILLS')for(const [x,y] of [[280,180],[1130,110],[1250,820]]){
      const grad=ctx.createRadialGradient(x,y,20,x,y,260);grad.addColorStop(0,'#3a503c');grad.addColorStop(1,'#5d674000');ctx.fillStyle=grad;ctx.beginPath();ctx.ellipse(x,y,280,150,-.2,0,7);ctx.fill()
      for(let n=0;n<4;n++){ctx.strokeStyle='#c7b98b20';ctx.beginPath();ctx.ellipse(x,y,100+n*45,45+n*25,-.2,0,7);ctx.stroke()}
    }
    if(theme==='JUNGLE')for(let n=0;n<75;n++){
      const x=(n*193+75)%1560,y=(n*137+65)%940
      if((y>700&&x<300)||(y>650&&x>1050))continue
      ctx.fillStyle='#142c2060';ctx.beginPath();ctx.ellipse(x+14,y+15,28,20,0,0,7);ctx.fill()
      ctx.fillStyle=n%2?'#315733':'#406b40';ctx.beginPath();ctx.arc(x,y,22+n%9,0,7);ctx.fill();ctx.strokeStyle='#71915755';ctx.beginPath();ctx.moveTo(x-12,y);ctx.lineTo(x+14,y-12);ctx.stroke()
    }
  }
  for(const a of state.areas.filter(a=>a.layer===state.layer)){
    ctx.fillStyle=({TRENCH:'#493b2b',ROAD:'#6d6e65',PALACE:'#738a57',TUNNEL:'#665039',ROOM:'#7a6246',ENTRY:'#302c1c',GATE:'#a39472',BUNKER:'#535844'} as Record<string,string>)[a.kind]||'#444'
    ctx.fillRect(a.x,a.y,a.width,a.height)
    if(a.kind==='TRENCH'||a.kind==='TUNNEL'){
      ctx.strokeStyle='#261e1688';ctx.lineWidth=8;ctx.strokeRect(a.x+4,a.y+4,a.width-8,a.height-8)
      ctx.strokeStyle='#ba9a6644';ctx.lineWidth=2
      for(let x=a.x+10;x<a.x+a.width-5;x+=18){ctx.beginPath();ctx.moveTo(x,a.y+8);ctx.lineTo(x,a.y+a.height-8);ctx.stroke()}
    }
    if(a.kind==='GATE'){
      if(state.objective>=2)continue
      ctx.strokeStyle='#35382e';ctx.lineWidth=4;for(let y=a.y;y<a.y+a.height;y+=14){ctx.beginPath();ctx.moveTo(a.x,y);ctx.lineTo(a.x+a.width,y);ctx.stroke()}
    }
    ctx.font='12px sans-serif';ctx.fillStyle='#f4dfab';ctx.fillText(a.label,a.x+5,a.y-9)
  }
  if(!underground){
    for(const wall of state.walls){
      ctx.fillStyle='#111b1766';ctx.fillRect(wall.x+8,wall.y+10,wall.width,wall.height)
      ctx.fillStyle=theme==='CITY'?'#9b9a83':'#61634f';ctx.fillRect(wall.x,wall.y,wall.width,wall.height)
      ctx.fillStyle=theme==='CITY'?'#b0ad96':'#75745a';ctx.fillRect(wall.x+6,wall.y+6,wall.width-12,wall.height-12)
      ctx.strokeStyle='#292e28';ctx.lineWidth=2;ctx.strokeRect(wall.x,wall.y,wall.width,wall.height)
      if(theme==='HILLS')for(let x=wall.x+5;x<wall.x+wall.width;x+=16){ctx.fillStyle='#b5a279';ctx.fillRect(x,wall.y-4,13,9)}
    }
    if(theme==='CITY'){
      ctx.fillStyle='#d9d5bd';ctx.fillRect(1510,260,70,440);ctx.fillStyle='#474f49'
      for(let y=275;y<680;y+=27){ctx.fillRect(1522,y,20,17);ctx.fillRect(1550,y,20,17)}
      ctx.fillStyle='#ece7cd';ctx.fillRect(1500,450,18,100)
      ctx.fillStyle='#d2c7a1';ctx.fillRect(1400,480,80,80)
      ctx.fillStyle='#727f68';ctx.beginPath();ctx.arc(1440,520,30,0,7);ctx.fill()
    }
  }
  const goal=state.objectives[state.objective]
  if(goal&&goal.layer===state.layer){
    const pulse=window.matchMedia('(prefers-reduced-motion: reduce)').matches?0:Math.sin(time/250)*3
    ctx.strokeStyle='#f1d482';ctx.lineWidth=2;ctx.beginPath();ctx.arc(goal.x,goal.y,27+pulse,0,7);ctx.stroke()
    ctx.fillStyle='#f1d482';ctx.font='bold 17px sans-serif';ctx.fillText(String(state.objective+1),goal.x-5,goal.y+6)
  }
  for(const unit of state.units){
    if(unit.hp<=0||(underground&&unit.id!==me.id))continue
    ctx.save();ctx.translate(unit.x,unit.y);ctx.rotate(unit.tank?unit.hullAngle:unit.angle)
    ctx.fillStyle='#07130b55';ctx.beginPath();ctx.ellipse(5,7,unit.tank?28:12,unit.tank?21:9,0,0,7);ctx.fill()
    if(unit.tank){
      ctx.fillStyle='#232e28';ctx.fillRect(-27,-24,54,10);ctx.fillRect(-27,14,54,10)
      ctx.fillStyle=unit.ally?'#626b3e':'#817963';ctx.fillRect(-24,-17,48,34)
      ctx.save();ctx.rotate(unit.angle-unit.hullAngle)
      ctx.fillStyle='#7b8051';ctx.beginPath();ctx.ellipse(0,0,15,13,0,0,7);ctx.fill()
      ctx.strokeStyle='#444e32';ctx.lineWidth=7;ctx.beginPath();ctx.moveTo(5,0);ctx.lineTo(39,0);ctx.stroke()
      ctx.restore()
      ctx.fillStyle='#e3dabb';ctx.font='bold 11px sans-serif';ctx.fillText(unit.id===me.id?'390':'843',-19,6)
    }else{
      ctx.fillStyle=unit.ally?'#747c48':'#a29270';ctx.fillRect(-9,-7,18,14)
      ctx.fillStyle='#c0a17c';ctx.beginPath();ctx.arc(3,0,6,0,7);ctx.fill()
      ctx.fillStyle=unit.ally?'#54653a':'#807b60';ctx.beginPath();ctx.arc(0,0,7,0,7);ctx.fill()
      ctx.strokeStyle='#302d25';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(6,5);ctx.lineTo(23,5);ctx.stroke()
    }
    ctx.restore()
    ctx.fillStyle='#1c221a';ctx.fillRect(unit.x-20,unit.y-36,40,4);ctx.fillStyle=unit.ally?'#aec486':'#d2957b';ctx.fillRect(unit.x-20,unit.y-36,40*unit.hp/unit.maxHealth,4)
    if(unit.ally){ctx.font='11px sans-serif';ctx.fillStyle='#f1e6c7';ctx.fillText(unit.id===me.id?'BẠN':unit.name,unit.x-20,unit.y-43)}
  }
  for(const supply of state.supplies??[])if(supply.layer===state.layer){
    ctx.fillStyle=supply.quantity>0?'#e2c074':'#66664e';ctx.fillRect(supply.x-11,supply.y-11,22,22)
    ctx.fillStyle='#1f2c20';ctx.font='bold 10px sans-serif';ctx.fillText(supply.kind==='FUEL'?'F':supply.kind==='REPAIR'?'+':'A',supply.x-4,supply.y+4)
  }
  if(me.tank&&!underground){ctx.strokeStyle='#d6dcac33';ctx.lineWidth=1;ctx.beginPath();ctx.arc(me.x,me.y,me.tacticalView?.range??700,0,7);ctx.stroke()}
  for(const shell of state.projectiles??[]){ctx.fillStyle='#edd39b';ctx.beginPath();ctx.arc(shell.x,shell.y,3,0,7);ctx.fill()}
  if(!underground)for(const shot of state.shots){
    ctx.strokeStyle=shot.blast?'#ffce80':'#ffebba';ctx.globalAlpha=shot.life/3;ctx.lineWidth=shot.blast?3:1
    ctx.beginPath();ctx.moveTo(shot.x,shot.y);ctx.lineTo(shot.tx,shot.ty);ctx.stroke()
    if(shot.blast){ctx.fillStyle='#ff994c44';ctx.beginPath();ctx.arc(shot.tx,shot.ty,110*(1-shot.life/4),0,7);ctx.fill()}
    ctx.globalAlpha=1
  }
  if(underground){
    const light=ctx.createRadialGradient(me.x,me.y,65,me.x,me.y,400);light.addColorStop(0,'#0000');light.addColorStop(1,'#000c');ctx.fillStyle=light;ctx.fillRect(0,0,1600,1000)
  }
  ctx.setTransform(dpr,0,0,dpr,0,0)
  const mx=w-174,my=h-118
  ctx.fillStyle='#102218e6';ctx.fillRect(mx,my,160,100);ctx.strokeStyle='#a6ad71';ctx.lineWidth=1;ctx.strokeRect(mx,my,160,100)
  for(const a of state.areas.filter(a=>a.layer===state.layer)){ctx.fillStyle='#9e946b77';ctx.fillRect(mx+a.x/10,my+a.y/10,a.width/10,a.height/10)}
  for(const supply of state.supplies??[])if(supply.layer===state.layer){ctx.fillStyle=supply.quantity>0?'#e2c074':'#66664e';ctx.fillRect(mx+supply.x/10-2,my+supply.y/10-2,4,4)}
  for(const unit of state.units.filter(a=>a.hp>0&&(!underground||a.id===me.id))){ctx.fillStyle=unit.id===me.id?'#fff5bd':unit.ally?'#9ec585':'#dd8865';ctx.fillRect(mx+unit.x/10-2,my+unit.y/10-2,4,4)}
  if(goal){ctx.strokeStyle='#f1d482';ctx.beginPath();ctx.arc(mx+goal.x/10,my+goal.y/10,5,0,7);ctx.stroke()}
  ctx.fillStyle='#d9ddbb';ctx.font='10px sans-serif';ctx.fillText(underground?'SƠ ĐỒ ĐỊA ĐẠO':'BẢN ĐỒ CHIẾN TRƯỜNG',mx,my-5)

}
