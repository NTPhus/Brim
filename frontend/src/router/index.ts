import { createRouter, createWebHistory } from 'vue-router'
import { joinRoom, leaveRoom } from '@/api/rooms'
import { useGameSession } from '@/stores/gameSession'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/vietnam', name: 'vietnam-hub', component: () => import('@/views/VietnamHubView.vue') },
    { path: '/game/vietnam/:roomId', name: 'vietnam-campaign', meta: { gameViewport: true }, component: () => import('@/views/VietnamGameView.vue') },
    { path: '/warlords', name: 'warlords-hub', component: () => import('@/views/WarlordsHubView.vue') },
    { path: '/game/warlords-campaign/:roomId', name: 'warlords-campaign', meta: { gameViewport: true }, component: () => import('@/views/WarlordsGameView.vue'), props: { mode: 'CAMPAIGN' } },
    { path: '/game/warlords-duel/:roomId', name: 'warlords-duel', meta: { gameViewport: true }, component: () => import('@/views/WarlordsGameView.vue'), props: { mode: 'DUEL' } },
    { path: '/game/caro/:roomId', name: 'caro', component: () => import('@/views/GridDuelView.vue'), props: { kind: 'CARO' } },
    { path: '/game/othello/:roomId', name: 'othello', component: () => import('@/views/GridDuelView.vue'), props: { kind: 'OTHELLO' } },
    { path: '/game/battleship/:roomId', name: 'battleship', component: () => import('@/views/BattleshipView.vue') },
    { path: '/game/match-three/:roomId', name: 'match-three', component: () => import('@/views/MatchThreeView.vue') },
    { path: '/game/bomberman/:roomId', name: 'bomberman', component: () => import('@/views/BombermanView.vue') },
    {
      path: '/game/connect-four/:roomId',
      name: 'connect-four',
      component: () => import('@/views/ConnectFourView.vue'),
      props: true
    },
    {
      path: '/',
      name: 'lobby',
      component: () => import('@/views/LobbyView.vue')
    },
    {
      path: '/room/:roomId',
      name: 'room',
      component: () => import('@/views/RoomView.vue')
    },
    {
      path: '/game/snake/:roomId',
      name: 'snake',
      component: () => import('@/views/SnakeView.vue'),
      props: true
    },
    {
      path: '/game/tank/:roomId',
      name: 'tank',
      meta: { gameViewport: true },
      component: () => import('@/views/TankView.vue'),
      props: true
    },
    {
      path: '/game/chess/:roomId',
      name: 'chess',
      component: () => import('@/views/ChessView.vue'),
      props: true
    },
    {
      path: '/game/chinese-chess/:roomId',
      name: 'chinese-chess',
      component: () => import('@/views/ChineseChessView.vue'),
      props: true
    }
  ]
})

router.beforeEach(async to => {
  if (!to.params.roomId) return true
  const session = useGameSession()
  if (!session.authenticated) return '/'
  try {
    const room = await joinRoom(String(to.params.roomId), { playerId: session.player.id,
      sessionId: session.player.sessionId, username: session.player.username })
    session.setCurrentRoom(room)
    return true
  } catch { return '/' }
})

router.afterEach((to, from, failure) => {
  const previousRoomId = from.params.roomId
  if (failure || !previousRoomId || previousRoomId === to.params.roomId) return
  const session = useGameSession()
  if (!session.authenticated || !session.currentRoom) return
  // Navigation through the header or browser Back also leaves the old room.
  void leaveRoom(String(previousRoomId), session.player.id).catch(error => {
    console.error('Failed to leave previous room:', error)
  })
  if (session.currentRoom.id === previousRoomId) session.setCurrentRoom(null)
})

export default router
