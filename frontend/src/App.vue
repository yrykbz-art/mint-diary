<script setup lang="ts">
import { ref, provide, onMounted, onBeforeUnmount } from 'vue'
import { api, errorMessage, type ImageAsset } from './api'
import { appKey, type ConfirmOptions } from './context'
import Icon from './components/Icon.vue'
import BaseDialog from './components/BaseDialog.vue'

const today = ref('')
const connectionError = ref('')
const connecting = ref(true)
const toast = ref<{ message: string; type: 'success' | 'error' } | null>(null)
const question = ref<ConfirmOptions | null>(null)
const viewer = ref<{ images: ImageAsset[]; index: number } | null>(null)
let answer: ((value: boolean) => void) | null = null
let toastTimer: ReturnType<typeof setTimeout>
let dayTimer: ReturnType<typeof setInterval>

function notify(message: string, type: 'success' | 'error' = 'success') {
  clearTimeout(toastTimer)
  toast.value = { message, type }
  toastTimer = setTimeout(() => { toast.value = null }, type === 'error' ? 6500 : 3200)
}
function confirm(options: ConfirmOptions): Promise<boolean> {
  if (answer) return Promise.resolve(false)
  question.value = options
  return new Promise(resolve => { answer = resolve })
}
function resolveQuestion(value: boolean) { const resolve = answer; answer = null; question.value = null; resolve?.(value) }
function moveImage(offset: number) { if (viewer.value) viewer.value.index = (viewer.value.index + offset + viewer.value.images.length) % viewer.value.images.length }

provide(appKey, { today, notify, confirm, preview: (images, index) => { viewer.value = { images: [...images], index } } })
async function connect() {
  if (!today.value) connecting.value = true
  try { const health = await api.health(); today.value = health.today; connectionError.value = '' }
  catch (error) { if (!today.value) connectionError.value = errorMessage(error) }
  finally { connecting.value = false }
}
onMounted(() => { void connect(); dayTimer = setInterval(connect, 60000); window.addEventListener('focus', connect) })
onBeforeUnmount(() => { clearTimeout(toastTimer); clearInterval(dayTimer); window.removeEventListener('focus', connect) })
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/" aria-label="日记首页"><Icon name="leaf" :size="27" /><span>日记</span></RouterLink>
      <nav aria-label="主要导航">
        <RouterLink to="/" class="nav-link" exact-active-class="active"><Icon name="calendar" :size="23" /><span>日历日记</span></RouterLink>
        <RouterLink to="/anniversaries" class="nav-link" active-class="active"><Icon name="heart" :size="23" /><span>纪念日</span></RouterLink>
      </nav>
      <div class="sidebar-note"><span class="tiny-leaf"><Icon name="leaf" :size="16" /></span>日子慢慢过，<br />回忆慢慢存。</div>
    </aside>

    <main class="main-content">
      <div v-if="connecting" class="connection-state"><Icon name="loading" :size="28" /><p>正在打开你的日记…</p></div>
      <div v-else-if="connectionError" class="connection-state"><Icon name="alert" :size="32" /><h1>暂时没有连接上</h1><p>{{ connectionError }}</p><button class="button primary" @click="connect"><Icon name="refresh" :size="17" />重新连接</button></div>
      <RouterView v-else />
    </main>
  </div>

  <Transition name="toast"><div v-if="toast" class="toast-message" :class="toast.type" :role="toast.type === 'error' ? 'alert' : 'status'"><Icon :name="toast.type === 'success' ? 'check' : 'alert'" :size="19" />{{ toast.message }}<button aria-label="关闭提示" @click="toast = null"><Icon name="close" :size="16" /></button></div></Transition>

  <BaseDialog :open="!!question" :title="question?.title || ''" priority @close="resolveQuestion(false)">
    <p class="confirm-text">{{ question?.text }}</p>
    <footer class="dialog-actions"><button class="button secondary" @click="resolveQuestion(false)">{{ question?.cancelLabel || '取消' }}</button><button class="button" :class="question?.danger ? 'danger' : 'primary'" @click="resolveQuestion(true)">{{ question?.confirmLabel || '确定' }}</button></footer>
  </BaseDialog>

  <BaseDialog :open="!!viewer" title="图片预览" wide priority @close="viewer = null">
    <div v-if="viewer" class="image-viewer" @keydown.left.prevent="moveImage(-1)" @keydown.right.prevent="moveImage(1)">
      <img :src="viewer.images[viewer.index]?.url" :alt="viewer.images[viewer.index]?.originalName" />
      <footer><button class="icon-button" aria-label="上一张图片" :disabled="viewer.images.length < 2" @click="moveImage(-1)"><Icon name="previous" /></button><span>{{ viewer.index + 1 }} / {{ viewer.images.length }}</span><button class="icon-button" aria-label="下一张图片" :disabled="viewer.images.length < 2" @click="moveImage(1)"><Icon name="next" /></button></footer>
    </div>
  </BaseDialog>
</template>
