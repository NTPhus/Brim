import { createRouter, createWebHistory } from 'vue-router'
import { joinRoom, leaveRoom } from '@/api/rooms'
import { useGameSession } from '@/stores/gameSession'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
