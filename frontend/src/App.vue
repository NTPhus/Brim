<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">B</span>
        <span><strong>BRIM</strong><small>GAME ARENA</small></span>
      </RouterLink>
      <nav>
        <RouterLink to="/">Đấu trường</RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: radial-gradient(circle at 10% 10%, rgba(52, 152, 219, .13), transparent 28%), radial-gradient(circle at 90% 80%, rgba(233, 69, 96, .11), transparent 30%), #0b1020;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(8, 12, 28, 0.76);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.brand { display: flex; align-items: center; gap: .7rem; color: white; text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 11px; background: linear-gradient(135deg, #fb7185, #7c3aed); font-weight: 900; box-shadow: 0 0 24px rgba(251,113,133,.28); }
.brand > span:last-child { display: flex; flex-direction: column; line-height: 1.05; }
.brand strong { letter-spacing: .12em; }
.brand small { color: #67e8f9; font-size: .58rem; letter-spacing: .18em; margin-top: .2rem; }

nav a {
  color: #fff;
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  transition: background 0.2s;
}

nav a:hover,
nav a.router-link-active {
  background: rgba(233, 69, 96, 0.2);
}

.app-main {
  flex: 1;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
}

@media (max-width: 640px) {
  .app-header { padding: .75rem 1rem; }
  .app-main { padding: 1rem; }
}
</style>
