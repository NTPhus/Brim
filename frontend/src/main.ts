if (typeof (window as any).global === 'undefined') {
  (window as any).global = window
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { useGameSession } from './stores/gameSession'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
async function start() {
  await useGameSession().initialize()
  app.use(router)
  await router.isReady()
  app.mount('#app')
}
void start().catch(error => {
  const root = document.getElementById('app')!
  root.replaceChildren()
  const text = document.createElement('p')
  text.textContent = error instanceof Error ? error.message : 'Không thể kết nối máy chủ'
  const button = document.createElement('button')
  button.textContent = 'Thử lại'
  button.onclick = () => window.location.reload()
  root.append(text, button)
})
