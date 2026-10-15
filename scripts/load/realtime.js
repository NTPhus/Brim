// k6 run -e BASE_URL=http://localhost:8088 -e GAME=TANK scripts/load/realtime.js
import http from 'k6/http'
import ws from 'k6/ws'
import { check, sleep } from 'k6'
import { Counter, Trend, Rate } from 'k6/metrics'
const snapshotBytes = new Counter('snapshot_bytes')
const snapshotAge = new Trend('snapshot_age_ms')
const received = new Rate('received_snapshot')
const base = __ENV.BASE_URL || 'http://localhost:8088'
const game = __ENV.GAME || 'TANK'
const roomCount = Number(__ENV.ROOMS || 3)
export const options = {
  vus: Number(__ENV.USERS || 12), duration: __ENV.DURATION || '60s',
  thresholds: { http_req_failed: ['rate<0.01'], received_snapshot: ['rate>0.95'], snapshot_age_ms: ['p(95)<500'] }
}
export function setup() {
  const run = Date.now()
  const rooms = []
  for (let i = 0; i < roomCount; i++) {
    const hostId = `load-${run}-${i}`
    const response = http.post(`${base}/api/rooms`, JSON.stringify({ name: `Load ${i}`, gameType: game,
      maxPlayers: 16, hostId, hostUsername: hostId }), { headers: { 'Content-Type': 'application/json' } })
    if (!check(response, { 'created room': r => r.status === 200 })) throw new Error(response.body)
    rooms.push({ id: response.json('id'), hostId })
  }
  return { rooms, run }
}
export default function (data) {
  const room = data.rooms[(__VU - 1) % data.rooms.length]
  const id = __VU <= roomCount ? room.hostId : `load-${data.run}-user-${__VU}`
  http.post(`${base}/api/rooms/${room.id}/join`, JSON.stringify({ playerId: id, sessionId: id, username: id }),
    { headers: { 'Content-Type': 'application/json' } })
  let snapshots = 0
  const kind = game === 'SNAKE' ? 'snake' : 'tank'
  const frame = (command, headers, body = '') => `${command}\n${headers}\n\n${body}\0`
  const response = ws.connect(`${base.replace(/^http/, 'ws')}/api/ws/game/websocket`, {}, socket => {
    socket.on('open', () => socket.send(frame('CONNECT', 'accept-version:1.2\nheart-beat:0,0')))
    socket.on('message', message => {
      if (message.startsWith('CONNECTED')) {
        socket.send(frame('SUBSCRIBE', `id:state\ndestination:/topic/game/${room.id}/state/${id}`))
        const body = JSON.stringify({ playerId: id, roomId: room.id, payload: { id, sessionId: id, username: id, ready: true } })
        socket.send(frame('SEND', `destination:/app/${kind}/${room.id}/join\ncontent-type:application/json`, body))
        socket.send(frame('SEND', `destination:/app/${kind}/${room.id}/ready\ncontent-type:application/json`,
          JSON.stringify({ roomId: room.id, playerId: id, payload: null })))
      }
      if (message.startsWith('MESSAGE')) {
        const body = message.slice(message.indexOf('\n\n') + 2).replace(/\0.*$/s, '')
        const state = JSON.parse(body)
        snapshots++
        snapshotBytes.add(body.length)
        if (state.serverTime) snapshotAge.add(Math.max(0, Date.now() - state.serverTime))
      }
    })
    socket.setInterval(() => {
      const payload = game === 'SNAKE' ? { angle: Date.now() / 3000, boosting: false }
        : { movingForward: true, turningRight: Math.floor(Date.now() / 1000) % 2 === 0, shooting: true }
      socket.send(frame('SEND', `destination:/app/${kind}/${room.id}/${game === 'SNAKE' ? 'move' : 'input'}\ncontent-type:application/json`,
        JSON.stringify({ roomId: room.id, playerId: id, payload })))
    }, 100)
    socket.setTimeout(() => socket.close(), 20000)
  })
  check(response, { 'websocket upgrade': r => r && r.status === 101 })
  received.add(snapshots > 0)
  sleep(1)
}
export function teardown(data) {
  data.rooms.forEach(room => http.del(`${base}/api/rooms/${room.id}?hostId=${encodeURIComponent(room.hostId)}`))
}
