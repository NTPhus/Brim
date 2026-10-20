<script setup lang="ts">
import { ref } from 'vue'
import { useGameSession } from '@/stores/gameSession'
import { useStompClient } from '@/composables/useStompClient'
defineProps<{disabled?:boolean}>()
const emit=defineEmits<{changed:[];busy:[value:boolean]}>()
const session=useGameSession(),stomp=useStompClient()
const mode=ref<'register'|'login'>('register'),username=ref(''),password=ref(''),saving=ref(false),error=ref('')
async function submit(){
  if(saving.value)return
  saving.value=true;emit('busy',true);error.value=''
  try{await stomp.disconnect();await session.authenticate(mode.value,username.value.trim(),password.value);emit('changed')}
  catch(cause){error.value=cause instanceof Error?cause.message:'Chưa thể đăng nhập.'}
  finally{password.value='';saving.value=false;emit('busy',false)}
}
</script>
<template>
  <section class="war-panel war-account">
    <div v-if="session.account" class="account-saved"><span class="account-emblem" aria-hidden="true">✓</span><div><strong>{{session.player.username}} · Tài khoản đã lưu</strong><p>Tướng, vàng, nâng cấp và chiến dịch được giữ khi đăng nhập lại, kể cả trên thiết bị khác.</p></div><span class="account-badge saved">Đã liên kết</span></div>
    <details v-else>
      <summary><span class="account-emblem" aria-hidden="true">⚑</span><span><strong>Giữ lại hành trình của bạn</strong><small>Lưu tiến độ vào tài khoản hoặc đăng nhập</small></span><span class="account-badge">Phiên guest <b aria-hidden="true">⌄</b></span></summary>
      <p>Đăng ký để giữ nguyên tiến độ guest hiện tại. Đăng nhập để tải tiến độ của tài khoản đã có.</p>
      <div class="account-tabs" aria-label="Cách kết nối tài khoản"><button type="button" class="war-button small" :class="{secondary:mode!=='register'}" :aria-pressed="mode==='register'" :disabled="disabled||saving" @click="mode='register';error=''">Tạo tài khoản</button><button type="button" class="war-button small" :class="{secondary:mode!=='login'}" :aria-pressed="mode==='login'" :disabled="disabled||saving" @click="mode='login';error=''">Đăng nhập</button></div>
      <form @submit.prevent="submit">
        <label>Tên đăng nhập<input v-model="username" required minlength="3" maxlength="32" pattern="[a-zA-Z0-9_]{3,32}" autocomplete="username" autocapitalize="none" spellcheck="false" :disabled="disabled||saving" placeholder="3–32 chữ, số hoặc dấu _"/></label>
        <label>Mật khẩu<input v-model="password" type="password" required minlength="8" maxlength="72" :autocomplete="mode==='register'?'new-password':'current-password'" :disabled="disabled||saving" placeholder="Ít nhất 8 ký tự"/></label>
        <button class="war-button" :disabled="disabled||saving">{{saving?'Đang kết nối…':mode==='register'?'Tạo tài khoản và giữ tiến độ':'Đăng nhập tài khoản'}}</button>
      </form>
      <p v-if="error" class="war-error" role="alert">{{error}}</p>
      <p class="account-note">Đăng nhập ở nơi khác sẽ kết thúc phiên tài khoản cũ.</p>
    </details>
  </section>
</template>
<style scoped>
.war-account{margin-bottom:16px}.war-account>strong{color:#b4d0a0}.war-account p{font-size:.8rem;color:#b9af97;line-height:1.6;margin:.5rem 0}.war-account summary{cursor:pointer;color:#e1c48b;font-weight:700}.account-tabs{display:flex;gap:8px;margin:12px 0}.war-account form{display:grid;grid-template-columns:1fr 1fr auto;align-items:end;gap:10px}.war-account label{display:flex;flex-direction:column;gap:6px;font-size:.75rem;color:#dbc9a4}.war-account input{padding:10px;border:1px solid #a68d5655;border-radius:7px;background:#111d25;color:#f1e2c5;width:100%;min-width:0}.war-account .account-note{font-size:.68rem}@media(max-width:700px){.war-account form{grid-template-columns:1fr}.account-tabs button{flex:1}}
.war-account{padding:14px 18px;background:#1c282533;border-color:#bd9a5d30;box-shadow:none;border-radius:10px}.war-account summary{display:flex;align-items:center;gap:12px;list-style:none}.war-account summary::-webkit-details-marker{display:none}.war-account summary>span:nth-child(2){display:flex;flex-direction:column;gap:5px;flex:1;min-width:0}.war-account summary strong{font-size:.75rem;font-weight:500;color:#d2c09a}.war-account summary small{font-size:.62rem;color:#a99e87;font-weight:400;line-height:1.6}.account-emblem{display:grid;place-items:center;width:31px;height:31px;flex-shrink:0;border:1px solid #bda06430;border-radius:50%;color:#bca06d;background:#8d874a0a}.account-badge{margin-left:auto;font-size:.55rem;font-weight:400;color:#c1b28d;white-space:nowrap}.account-badge b{font-size:.9rem;margin-left:7px;color:#d9bf7d;font-weight:400}.war-account details[open] summary{padding-bottom:14px;margin-bottom:12px;border-bottom:1px solid #bd9a5d25}.war-account details[open] .account-badge b{display:inline-block;transform:rotate(180deg)}.war-account p{font-size:.71rem;line-height:1.8}.war-account .account-note{font-size:.6rem}.war-account .war-error{color:#efb5a7}.account-tabs .war-button{font-size:.69rem}.war-account label{font-size:.68rem}.war-account input{font-size:.75rem;min-height:42px;box-sizing:border-box}.war-account input:focus-visible{outline:2px solid #d8c28e;outline-offset:2px}.war-account form>.war-button{font-size:.7rem;min-height:42px}.account-saved{display:flex;gap:12px;align-items:center}.account-saved>div{flex:1;min-width:0}.account-saved strong{font-size:.73rem;color:#c3d1ae;line-height:1.6}.account-saved p{font-size:.64rem;margin:4px 0 0}.account-saved .account-emblem{color:#bdd09a;border-color:#a3bc8350}.account-badge.saved{font-size:.54rem;color:#a9c093;padding:5px 7px;border:1px solid #a3bc8326;border-radius:4px}
@media(max-width:560px){.war-account{padding:13px}.war-account summary{gap:9px}.war-account summary strong{font-size:.7rem}.war-account summary small{font-size:.57rem}.account-emblem{width:26px;height:26px;font-size:.85rem}.account-badge{font-size:.49rem}.account-badge b{margin-left:4px}.account-saved{gap:9px}.account-saved .account-badge{display:none}.war-account input{font-size:1rem}.account-saved strong{font-size:.69rem}.account-saved p{font-size:.59rem}}
</style>
