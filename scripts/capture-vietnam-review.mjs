// Run with: node --experimental-websocket scripts/capture-vietnam-review.mjs <mode>
// Requires a dedicated Chrome profile with CDP on localhost:9224 and local BRIM on :3000.
import fs from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
const mode=process.argv[2]??'init';
if(mode==='pdf'){
 const name=process.argv[3]??'VIETNAM_CAMPAIGN_REVIEW';
 if(!['VIETNAM_CAMPAIGN_REVIEW','VIETNAM_CAMPAIGN'].includes(name))throw Error('Unknown document');
 const source=new URL('../docs/'+name+'.html',import.meta.url);
 const target=fileURLToPath(new URL('../docs/'+name+'.pdf',import.meta.url));
 const profile=await fs.mkdtemp('/tmp/brim-review-pdf-');
 await promisify(execFile)(process.env.BRIM_CHROME_EXECUTABLE??'/usr/bin/google-chrome',[
  '--headless=new','--no-sandbox','--disable-dev-shm-usage','--no-pdf-header-footer',
  '--print-to-pdf='+target,'--user-data-dir='+profile,source.href
 ],{timeout:120000});
 console.log('PDF generated:',target);process.exit(0);
}
const out = new URL('../docs/images/vietnam-review/', import.meta.url);
const tabs = await (await fetch('http://127.0.0.1:9224/json')).json();
const tab = tabs.find(t => t.type === 'page');
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, {once:true}));
let id = 0; const pending = new Map();
ws.addEventListener('message', ({data}) => {const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}});
function call(method,params={}) {return new Promise((resolve,reject)=>{const n=++id;pending.set(n,{resolve,reject});ws.send(JSON.stringify({id:n,method,params}))})}
async function evaluate(expression) {const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value}
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms));
async function shot(name){await sleep(350);const r=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});await fs.writeFile(new URL(name+'.png',out),Buffer.from(r.data,'base64'));const meta=await evaluate(`typeof reviewState==='undefined'?{url:location.pathname}:{url:location.pathname,chapter:reviewState.chapter.id,objective:reviewState.objective,layer:reviewState.layer,status:reviewState.status,hp:reviewState.units[0].hp,ammo:reviewState.ammo,reserve:reviewState.reserveAmmo,tactical:reviewState.units[0].tacticalView,revision:reviewState.revision}`);await fs.writeFile(new URL(name+'.json',out),JSON.stringify({capturedAt:new Date().toISOString(),viewport:await evaluate('({width:innerWidth,height:innerHeight})'),...meta},null,2));console.log('Captured',name)}
await call('Page.enable');await call('Runtime.enable');
await call('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
if(mode==='init'){
 await call('Page.navigate',{url:'http://localhost:3000/vietnam'});await sleep(2500);
 console.log(await evaluate(`fetch('/api/auth/session').then(r=>r.json())`));
 await shot('00-hub-initial');
}else if(mode==='hub'){
 await evaluate('location.reload()');await sleep(1500);await shot('00-hub');
 for(let n=0;n<3;n++){await evaluate(`document.querySelectorAll('.campaign-chapter')[${n}].scrollIntoView({block:'center'})`);await shot(`0${n+1}-briefing`)}
}else if(mode==='start'){
 const chapter=Number(process.argv[3]);
 console.log(await evaluate(`(async()=>{const {apiFetch}=await import('/src/api/session.ts');const r=await apiFetch('/api/rooms',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:'Review capture ${chapter}',gameType:'VIETNAM_CAMPAIGN',maxPlayers:1,vietnamChapter:${chapter}})});if(!r.ok)throw Error(await r.text());const room=await r.json();window.reviewRoom=room.id;const {useStompClient}=await import('/src/composables/useStompClient.ts');window.reviewSocket=useStompClient();await reviewSocket.connect();const identity=await(await fetch('/api/auth/session')).json();window.reviewPlayer=identity.id;window.reviewUnsub=reviewSocket.subscribe('/topic/game/'+room.id+'/state/'+identity.id,s=>window.reviewState=s);reviewSocket.publish('/app/turn/'+room.id+'/join',{playerId:identity.id,payload:{id:identity.id,ready:true}});return room.id})()`));
 await sleep(1200);
 await evaluate(`(async()=>{const {default:router}=await import('/src/router/index.ts');await router.push('/vietnam');await new Promise(r=>setTimeout(r,150));await router.push('/game/vietnam/'+reviewRoom)})()`);
 await sleep(1200);await shot(`0${chapter}-start`);
 console.log(await evaluate('({chapter:reviewState.chapter.id,me:reviewState.units[0],objective:reviewState.objective})'));
}else if(mode==='shot'){
 await shot(process.argv[3]);console.log(await evaluate('({chapter:reviewState.chapter.id,objective:reviewState.objective,layer:reviewState.layer,status:reviewState.status,me:reviewState.units[0]})'));
}else if(mode==='eval'){
 console.log(await evaluate(await fs.readFile(process.argv[3],'utf8')));
}else if(mode==='route'){
 const plan=JSON.parse(await fs.readFile(process.argv[3],'utf8'));
 await evaluate(await fs.readFile(new URL('./vietnam-review-controls.js',import.meta.url),'utf8'));
 for(const step of plan){
  if(step.move) console.log(await evaluate(`reviewWalk(${JSON.stringify(step.move)})`));
  if(step.fight) console.log(await evaluate(`reviewFight(${step.fight})`));
  if(step.action) {await evaluate(`reviewAction(${JSON.stringify(step.action)})`);await sleep(450)}
  if(step.wait) await sleep(step.wait);
  if(step.shot) await shot(step.shot);
 }
}else if(mode==='mobile'){
 await call('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await sleep(500);await shot(process.argv[3]);
}
ws.close();
