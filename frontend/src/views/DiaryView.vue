<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api, errorMessage, checkImage, type ImageAsset, type Diary, type CalendarDay } from '../api'
import { calendarCells, dateLabel, monthLabel, shiftMonth, weekLabel } from '../dates'
import { useApp } from '../context'
import Icon from '../components/Icon.vue'

const { today, notify, confirm, preview } = useApp()
const selected = ref(today.value)
const month = ref(today.value.slice(0, 7))
const monthPicker = ref(false)
const days = ref<Record<string, CalendarDay>>({})
const content = ref('')
const images = ref<ImageAsset[]>([])
const originalContent = ref('')
const originalIds = ref<string[]>([])
const stagedIds = new Set<string>()
const exists = ref(false)
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const deleting = ref(false)
const loadError = ref('')
const calendarError = ref('')
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
let calendarSequence = 0

const cells = computed(() => calendarCells(month.value))
const dirty = computed(() => content.value !== originalContent.value || JSON.stringify(images.value.map(image => image.id)) !== JSON.stringify(originalIds.value))
const busy = computed(() => loading.value || saving.value || uploading.value || deleting.value)
const empty = computed(() => !content.value.trim() && images.value.length === 0)
const selectedMarks = computed(() => days.value[selected.value]?.anniversaries || [])

function setDiary(diary: Diary) {
  content.value = diary.content; images.value = diary.images; exists.value = diary.exists
  originalContent.value = diary.content; originalIds.value = diary.images.map(image => image.id)
}
async function loadDiary() {
  loading.value = true; loadError.value = ''
  try { setDiary(await api.diary(selected.value)) }
  catch (error) { loadError.value = errorMessage(error) }
  finally { loading.value = false }
}
async function loadCalendar() {
  const sequence = ++calendarSequence
  calendarError.value = ''
  try {
    const result = await api.calendar(month.value)
    if (sequence === calendarSequence) days.value = Object.fromEntries(result.days.map(day => [day.date, day]))
  } catch (error) { if (sequence === calendarSequence) calendarError.value = errorMessage(error) }
}
async function discardStaged() {
  const ids = [...stagedIds]; stagedIds.clear()
  await Promise.allSettled(ids.map(id => api.discardImage(id)))
}
async function mayLeave() {
  if (busy.value) { notify('请等当前操作完成后再切换。', 'error'); return false }
  if (!dirty.value) return true
  return confirm({ title: '这一天还没有保存', text: '切换后，刚刚修改的文字和图片将不会保留。', confirmLabel: '放弃修改', cancelLabel: '继续编辑' })
}
async function chooseDay(date: string) {
  if (date === selected.value || !(await mayLeave())) return
  await discardStaged()
  selected.value = date
  if (month.value !== date.slice(0, 7)) { month.value = date.slice(0, 7); void loadCalendar() }
  await loadDiary()
}
function browseMonth(amount: number) { month.value = shiftMonth(month.value, amount); void loadCalendar() }
function changeMonth(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (/^\d{4}-(0[1-9]|1[0-2])$/.test(value) && Number(value.slice(0, 4)) >= 1000) { month.value = value; monthPicker.value = false; void loadCalendar() }
}
async function backToToday() {
  if (selected.value === today.value) { month.value = today.value.slice(0, 7); void loadCalendar() }
  else await chooseDay(today.value)
}
async function save() {
  if (busy.value || !dirty.value || empty.value || loadError.value) return
  saving.value = true
  try { setDiary(await api.saveDiary(selected.value, content.value, images.value.map(image => image.id))); stagedIds.clear(); notify('这一天，已经好好收藏。'); await loadCalendar() }
  catch (error) { notify(errorMessage(error), 'error') }
  finally { saving.value = false }
}
async function addFiles(files: File[]) {
  if (busy.value || loadError.value || !files.length) return
  if (images.value.length + files.length > 20) { notify('每篇日记最多添加 20 张图片。', 'error'); return }
  const invalid = files.map(checkImage).find(Boolean)
  if (invalid) { notify(invalid, 'error'); return }
  uploading.value = true
  try {
    for (const file of files) { const image = await api.upload(file); stagedIds.add(image.id); images.value.push(image) }
  } catch (error) { notify(errorMessage(error), 'error') }
  finally { uploading.value = false; if (fileInput.value) fileInput.value.value = '' }
}
function onFileChange(event: Event) { void addFiles(Array.from((event.target as HTMLInputElement).files || [])) }
function dropFiles(event: DragEvent) { dragging.value = false; void addFiles(Array.from(event.dataTransfer?.files || [])) }
async function removeImage(image: ImageAsset) {
  if (busy.value) return
  images.value = images.value.filter(item => item.id !== image.id)
  if (stagedIds.has(image.id)) { stagedIds.delete(image.id); await api.discardImage(image.id).catch(() => undefined) }
}
async function deleteDiary() {
  if (busy.value || !exists.value) return
  const accepted = await confirm({ title: '删除这篇日记？', text: `${dateLabel(selected.value)}的文字和图片将一起删除。`, confirmLabel: '删除日记', danger: true })
  if (!accepted) return
  deleting.value = true
  try { await api.deleteDiary(selected.value); await discardStaged(); setDiary({ date: selected.value, content: '', images: [], exists: false, createdAt: null, updatedAt: null }); notify('日记已删除。'); await loadCalendar() }
  catch (error) { notify(errorMessage(error), 'error') }
  finally { deleting.value = false }
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || uploading.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(async () => { if (!(await mayLeave())) return false; await discardStaged(); return true })
onMounted(() => { void loadDiary(); void loadCalendar(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
watch(today, () => { void loadCalendar() })
</script>

<template>
  <header class="page-heading"><div><h1>日历日记</h1><p>把平凡的日子，认真收藏。</p></div></header>
  <div class="diary-workspace">
    <section class="surface calendar-panel" aria-label="日历">
      <header class="calendar-heading">
        <button class="month-title" aria-label="选择月份" :aria-expanded="monthPicker" @click="monthPicker = !monthPicker">{{ monthLabel(month) }}</button>
        <div class="calendar-controls"><button class="icon-button" aria-label="上个月" @click="browseMonth(-1)"><Icon name="left" :size="18" /></button><button class="today-button" @click="backToToday">今天</button><button class="icon-button" aria-label="下个月" @click="browseMonth(1)"><Icon name="right" :size="18" /></button></div>
      </header>
      <div v-if="monthPicker" class="month-chooser"><label for="month-picker">选择年月</label><input id="month-picker" type="month" :value="month" min="1000-01" max="9999-12" @change="changeMonth" /></div>
      <div v-if="calendarError" class="inline-error compact" role="alert">{{ calendarError }}<button @click="loadCalendar">重试</button></div>
      <div class="week-labels" aria-hidden="true"><span v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</span></div>
      <div class="calendar-grid" :aria-label="monthLabel(month)">
        <button v-for="cell in cells" :key="cell.date" class="day-cell" :class="{ outside: !cell.current, selected: cell.date === selected, 'is-today': cell.date === today }" :aria-label="`${cell.valid ? dateLabel(cell.date) : '日期超出范围'}${days[cell.date]?.hasDiary ? '，有日记' : ''}${days[cell.date]?.anniversaries.length ? '，' + days[cell.date]?.anniversaries.map(item => item.title).join('、') : ''}`" :aria-pressed="cell.date === selected" :title="days[cell.date]?.anniversaries.map(item => item.title).join('、') || days[cell.date]?.preview" :disabled="busy || !cell.valid" @click="chooseDay(cell.date)">
          <span class="day-number">{{ cell.number }}<span class="day-markers"><span v-if="days[cell.date]?.hasDiary" class="diary-dot"></span><Icon v-if="days[cell.date]?.anniversaries.length" class="anniversary-heart" name="heart" :size="12" /></span></span>
        </button>
      </div>
      <div class="calendar-legend"><span><i class="diary-dot"></i>有日记</span><span><Icon name="heart" :size="14" class="anniversary-heart" />纪念日</span></div>
      <div class="calendar-bottom-note"><Icon name="leaf" :size="18" /><p>点选一个日期，<br />回到那一天。</p></div>
    </section>

    <section class="surface editor-panel" :class="{ 'is-dragging': dragging }" aria-label="当天日记" @dragover.prevent="dragging = !busy" @dragleave.self="dragging = false" @drop.prevent="dropFiles">
      <header class="editor-heading">
        <div class="editor-date"><h2>{{ dateLabel(selected) }}</h2><span>{{ weekLabel(selected) }}</span></div>
        <div class="editor-actions"><span class="save-status" :class="{ changed: dirty }">{{ saving ? '正在保存' : dirty ? '未保存' : exists ? '已保存' : '还没有记录' }}</span><button v-if="exists" class="icon-button delete-diary" aria-label="删除当天日记" :disabled="busy" @click="deleteDiary"><Icon :name="deleting ? 'loading' : 'trash'" :size="18" /></button><button class="button primary save-button" :disabled="busy || !dirty || empty || !!loadError" @click="save"><Icon v-if="saving" name="loading" :size="17" />{{ saving ? '保存中…' : '保存日记' }}</button></div>
      </header>
      <div v-if="selectedMarks.length" class="selected-anniversaries"><RouterLink v-for="item in selectedMarks" :key="item.id" to="/anniversaries"><Icon name="heart" :size="14" />{{ item.title }}</RouterLink></div>
      <div v-if="loading" class="editor-loading"><Icon name="loading" :size="25" /><span>正在翻开这一天…</span></div>
      <div v-else-if="loadError" class="editor-error" role="alert"><Icon name="alert" :size="28" /><p>{{ loadError }}</p><button class="button secondary" @click="loadDiary">重新加载</button></div>
      <div v-else class="editor-body">
        <textarea v-model="content" aria-label="当天日记文字" class="diary-textarea" maxlength="20000" placeholder="今天发生了什么？&#10;写下想记住的小事…" :disabled="saving || deleting" spellcheck="false"></textarea>
        <div class="diary-images" :class="{ 'has-photos': images.length }">
          <div v-for="(image, index) in images" :key="image.id" class="diary-photo">
            <button class="photo-preview" :aria-label="`预览图片：${image.originalName}`" @click="preview(images, index)"><img :src="image.url" :alt="image.originalName" loading="lazy" /></button>
            <button class="remove-photo" :aria-label="`移除图片：${image.originalName}`" :disabled="busy" @click="removeImage(image)"><Icon name="close" :size="15" /></button>
          </div>
          <button v-if="images.length < 20" class="upload-tile" :class="{ 'first-upload': !images.length }" :disabled="busy" @click="fileInput?.click()"><Icon :name="uploading ? 'loading' : images.length ? 'plus' : 'imagePlus'" :size="images.length ? 28 : 32" /><span>{{ uploading ? '正在添加…' : '添加图片' }}</span><small v-if="!images.length">也可以把图片拖到这里</small></button>
          <input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple aria-label="选择日记图片" @change="onFileChange" />
        </div>
        <footer class="editor-footer"><span>每天一篇，慢慢记录。</span><span>{{ content.length.toLocaleString('zh-CN') }} / 20,000 字<span v-if="images.length"> · {{ images.length }} 张图片</span></span></footer>
      </div>
      <div v-if="dragging" class="drop-overlay"><Icon name="upload" :size="35" /><span>松开鼠标，添加图片</span></div>
    </section>
  </div>
</template>
