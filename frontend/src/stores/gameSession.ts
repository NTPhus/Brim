import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiFetch, refreshCsrf, resumeOrCreateGuest } from '@/api/session'
import type { GuestIdentity } from '@/api/session'
import type { GameRoom, Player } from '@/types/game'

export const useGameSession = defineStore('gameSession', () => {
  // IDs in localStorage are deliberately ignored; identity always comes from the server session.
  const player = ref<Player>({ id: '', username: '', sessionId: '', score: 0, ready: false, state: 'WAITING' })
  const currentRoom = ref<GameRoom | null>(null)
  const authenticated = ref(false)
  const account = ref(false)
  const profileError = ref('')
  async function initialize() {
    let username = localStorage.getItem('brim_username') || 'Player'
    if (!username.trim() || username.length > 32) username = 'Player'
    const identity = await resumeOrCreateGuest(username)
    adopt(identity)
    localStorage.removeItem('brim_player')
  }
  function adopt(identity:GuestIdentity){
    player.value={id:identity.id,username:identity.username,sessionId:identity.id,score:0,ready:false,state:'WAITING'}
    account.value=identity.account;authenticated.value=true;currentRoom.value=null
  }
  async function authenticate(mode:'register'|'login',username:string,password:string){
    await refreshCsrf()
    const response=await apiFetch(`/api/auth/${mode}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})})
    if(!response.ok)throw new Error(response.status===409?'Tên đăng nhập đã tồn tại hoặc bạn đã có tài khoản.':response.status===429?'Thử đăng nhập quá nhanh. Vui lòng chờ một lúc.':response.status===401?'Tên đăng nhập hoặc mật khẩu không đúng.':response.status===400?'Kiểm tra tên đăng nhập và mật khẩu (8–72 ký tự, tối đa 72 byte).':'Chưa thể kết nối tài khoản. Hãy thử lại.')
    adopt(await response.json());await refreshCsrf()
  }
  async function setUsername(username: string) {
    profileError.value = ''
    try {
      const response = await apiFetch('/api/auth/profile', { method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username }) })
      if (!response.ok) throw new Error('Tên phải có 1–32 ký tự và không chứa ký tự điều khiển')
      const identity = await response.json()
      player.value.username = identity.username
      localStorage.setItem('brim_username', identity.username)
    } catch (error) { profileError.value = error instanceof Error ? error.message : 'Không thể đổi tên' }
  }
  async function logout() {
    const response = await apiFetch('/api/auth/logout', { method: 'POST' })
    if (!response.ok && response.status !== 401) throw new Error('Không thể đăng xuất')
    authenticated.value = false
    account.value = false
    currentRoom.value = null
    player.value.id = ''
  }
  function setCurrentRoom(room: GameRoom | null) { currentRoom.value = room }
  return { player, currentRoom, authenticated, account, profileError, initialize, authenticate, setUsername, setCurrentRoom, logout }
})
