<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api, errorMessage, checkImage, type Anniversary, type ImageAsset } from '../api'
import { dateLabel, occursOn } from '../dates'
import { useApp } from '../context'
import Icon from '../components/Icon.vue'
import BaseDialog from '../components/BaseDialog.vue'

const { today, notify, confirm } = useApp()
const items = ref<Anniversary[]>([])
const loading = ref(true)
const error = ref('')
const formOpen = ref(false)
const editingId = ref<number>()
const title = ref('')
const date = ref(today.value)
const description = ref('')
const cover = ref<ImageAsset | null>(null)
const stagedIds = new Set<string>()
const saving = ref(false)
const uploading = ref(false)
const deleting = ref<number>()
const formError = ref('')
const initialState = ref('')
const fileInput = ref<HTMLInputElement>()
const formState = computed(() => JSON.stringify({ title: title.value, date: date.value, description: description.value, cover: cover.value?.id || null }))
const formDirty = computed(() => formOpen.value && formState.value !== initialState.value)
const busy = computed(() => saving.value || uploading.value)

async function load() {
  loading.value = true; error.value = ''
  try { items.value = await api.anniversaries() }
  catch (failure) { error.value = errorMessage(failure) }
  finally { loading.value = false }
}
function openForm(item?: Anniversary) {
  if (deleting.value !== undefined) return
  editingId.value = item?.id; title.value = item?.title || ''; date.value = item?.date || today.value
  description.value = item?.description || ''; cover.value = item?.coverImage || null
  stagedIds.clear(); formError.value = ''; initialState.value = formState.value; formOpen.value = true
}
async function discardStaged() {
  const ids = [...stagedIds]; stagedIds.clear()
  await Promise.allSettled(ids.map(id => api.discardImage(id)))
}
async function closeForm() {
  if (busy.value) return
  if (formDirty.value && !(await confirm({ title: '放弃这次修改？', text: '还没有保存的纪念日内容将不会保留。', confirmLabel: '放弃修改', cancelLabel: '继续编辑' }))) return
  await discardStaged(); formOpen.value = false
}
async function uploadCover(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || busy.value) return
  const failure = checkImage(file)
  if (failure) { formError.value = failure; return }
  uploading.value = true; formError.value = ''
  try {
    const image = await api.upload(file)
    if (cover.value && stagedIds.has(cover.value.id)) { stagedIds.delete(cover.value.id); await api.discardImage(cover.value.id).catch(() => undefined) }
    cover.value = image; stagedIds.add(image.id)
  } catch (failure) { formError.value = errorMessage(failure) }
  finally { uploading.value = false; if (fileInput.value) fileInput.value.value = '' }
}
async function removeCover() {
  if (busy.value) return
  if (cover.value && stagedIds.has(cover.value.id)) { const id = cover.value.id; stagedIds.delete(id); await api.discardImage(id).catch(() => undefined) }
  cover.value = null
}
async function submit() {
  if (busy.value) return
  if (!title.value.trim()) { formError.value = '给这个日子起个名字吧。'; return }
  if (!date.value || Number(date.value.slice(0, 4)) < 1000) { formError.value = '请选择包含年份的完整日期。'; return }
  saving.value = true; formError.value = ''
  try {
    await api.saveAnniversary({ title: title.value.trim(), date: date.value, description: description.value, coverImageId: cover.value?.id || null }, editingId.value)
    stagedIds.clear(); formOpen.value = false; notify(editingId.value === undefined ? '这个日子，记住了。' : '纪念日已更新。'); await load()
  } catch (failure) { formError.value = errorMessage(failure) }
  finally { saving.value = false }
}
async function remove(item: Anniversary) {
  if (deleting.value !== undefined) return
  if (!(await confirm({ title: `删除“${item.title}”？`, text: '这张纪念日卡片和日历中的年度标记将一起删除。当天的日记不受影响。', confirmLabel: '删除纪念日', danger: true }))) return
  deleting.value = item.id
  try { await api.deleteAnniversary(item.id); items.value = items.value.filter(row => row.id !== item.id); notify('纪念日已删除。') }
  catch (failure) { notify(errorMessage(failure), 'error') }
  finally { deleting.value = undefined }
}
function closeMenus(event: MouseEvent) {
  for (const menu of document.querySelectorAll<HTMLDetailsElement>('.card-menu[open]')) if (!menu.contains(event.target as Node)) menu.open = false
}
function beforeUnload(event: BeforeUnloadEvent) { if (formDirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(async () => {
  if (busy.value || deleting.value !== undefined) { notify('请等当前操作完成后再切换。', 'error'); return false }
  if (formDirty.value && !(await confirm({ title: '纪念日还没有保存', text: '切换页面后，刚刚修改的内容将不会保留。', confirmLabel: '放弃修改', cancelLabel: '继续编辑' }))) return false
  await discardStaged(); return true
})
onMounted(() => { void load(); document.addEventListener('click', closeMenus); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { document.removeEventListener('click', closeMenus); window.removeEventListener('beforeunload', beforeUnload) })
watch(today, load)
</script>

<template>
  <header class="page-heading anniversary-heading"><div><h1>纪念日</h1><p>有些日子，值得一直记得。</p></div><button class="button primary add-anniversary" @click="openForm()"><Icon name="plus" :size="21" />新增纪念日</button></header>

  <div v-if="loading" class="anniversary-loading"><Icon name="loading" :size="28" /><span>正在打开收藏的日子…</span></div>
  <div v-else-if="error" class="surface empty-state" role="alert"><Icon name="alert" :size="32" /><h2>暂时没有加载成功</h2><p>{{ error }}</p><button class="button secondary" @click="load"><Icon name="refresh" :size="17" />重新加载</button></div>
  <template v-else>
    <div v-if="!items.length" class="surface empty-state anniversary-empty"><div class="empty-heart"><Icon name="heart" :size="39" /></div><h2>把重要的日子，放在这里</h2><p>记录一个开始，往后的每一天都有意义。</p><button class="button primary" @click="openForm()"><Icon name="plus" :size="18" />添加第一个纪念日</button><span class="empty-footnote">生日、第一次旅行，或一个属于自己的新开始。</span></div>
    <div v-else class="anniversary-grid">
      <article v-for="(item, index) in items" :key="item.id" class="anniversary-card" :class="{ 'is-removing': deleting === item.id }">
        <div class="anniversary-cover" :class="{ 'without-cover': !item.coverImage, [`cover-tone-${index % 3}`]: !item.coverImage }">
          <img v-if="item.coverImage" :src="item.coverImage.url" :alt="`${item.title}的封面`" loading="lazy" />
          <div v-else class="cover-placeholder"><span class="cover-orbit orbit-one"></span><span class="cover-orbit orbit-two"></span><Icon :name="index % 2 ? 'leaf' : 'heart'" :size="46" /><span>值得记得的一天</span></div>
          <span v-if="occursOn(item.date, today)" class="anniversary-today"><Icon name="heart" :size="13" />今天是纪念日</span>
          <details class="card-menu"><summary :aria-label="`管理纪念日：${item.title}`"><Icon name="more" :size="21" /></summary><div class="card-menu-options"><button @click="openForm(item)"><Icon name="edit" :size="16" />编辑纪念日</button><button class="menu-danger" :disabled="deleting !== undefined" @click="remove(item)"><Icon name="trash" :size="16" />删除纪念日</button></div></details>
        </div>
        <div class="anniversary-card-body"><h2 :title="item.title">{{ item.title }}</h2><p class="original-date">{{ dateLabel(item.date) }}</p><div class="day-counter"><span class="counter-label">{{ item.future ? '还有' : '已经过' }}</span><div><strong>{{ (item.future ? item.daysUntilStart : item.daysPassed).toLocaleString('zh-CN') }}</strong><span class="counter-unit">天</span></div></div><p class="anniversary-description" :class="{ 'empty-description': !item.description }">{{ item.description || '每一个平凡日子，都值得珍藏。' }}</p></div>
      </article>
    </div>
    <p class="anniversary-note"><Icon name="calendar" :size="20" /><span>每年在同一公历日期，都会在日历中标记。</span></p>
  </template>

  <BaseDialog :open="formOpen" :title="editingId === undefined ? '新增纪念日' : '编辑纪念日'" :busy="busy" wide @close="closeForm">
    <form class="anniversary-form" @submit.prevent="submit">
      <div class="anniversary-form-body">
        <div class="cover-field"><label>封面照片 <span class="optional">选填</span></label><div class="cover-picker" :class="{ filled: cover }"><img v-if="cover" :src="cover.url" alt="纪念日封面预览" /><button v-else type="button" :disabled="busy" @click="fileInput?.click()"><Icon :name="uploading ? 'loading' : 'imagePlus'" :size="32" /><strong>{{ uploading ? '正在上传…' : '放一张喜欢的照片' }}</strong><span>也可以只用文字记住这一天</span></button></div><div v-if="cover" class="cover-actions"><button type="button" class="text-button" :disabled="busy" @click="fileInput?.click()">{{ uploading ? '上传中…' : '更换照片' }}</button><button type="button" class="text-button muted" :disabled="busy" @click="removeCover">移除</button></div><input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label="选择纪念日封面" @change="uploadCover" /></div>
        <div class="anniversary-fields"><label for="anniversary-title">名称 <span class="required-star">*</span></label><input id="anniversary-title" v-model="title" type="text" maxlength="100" required autofocus placeholder="例如：第一次旅行" :disabled="saving" /><label for="anniversary-date">原始日期 <span class="required-star">*</span></label><input id="anniversary-date" v-model="date" type="date" min="1000-01-01" max="9999-12-31" required :disabled="saving" /><p class="field-hint">请选择最初发生的年份，累计天数会从这一天算起。</p><label for="anniversary-description">想记住的话 <span class="optional">选填</span></label><textarea id="anniversary-description" v-model="description" rows="3" maxlength="500" placeholder="给这一天留一句话…" :disabled="saving"></textarea><span class="field-count">{{ description.length }} / 500</span></div>
      </div>
      <p v-if="formError" class="form-error" role="alert"><Icon name="alert" :size="17" />{{ formError }}</p>
      <footer class="dialog-actions"><button type="button" class="button secondary" :disabled="busy" @click="closeForm">取消</button><button type="submit" class="button primary" :disabled="busy"><Icon v-if="saving" name="loading" :size="17" />{{ saving ? '保存中…' : '保存纪念日' }}</button></footer>
    </form>
  </BaseDialog>
</template>
