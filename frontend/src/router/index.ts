import { createRouter, createWebHistory } from 'vue-router'

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

export default router
