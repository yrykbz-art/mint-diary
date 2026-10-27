<script setup lang="ts">
import { ref, watch, nextTick, useId, onBeforeUnmount } from 'vue'
import Icon from './Icon.vue'
const props = withDefaults(defineProps<{ open: boolean; title: string; wide?: boolean; busy?: boolean; priority?: boolean }>(), { wide: false, busy: false, priority: false })
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLElement>()
const titleId = useId()
let previousFocus: HTMLElement | null = null
watch(() => props.open, async (open) => {
  if (open) {
    previousFocus = document.activeElement as HTMLElement
    await nextTick()
    const first = dialog.value?.querySelector<HTMLElement>('[autofocus], input:not([type=file]), textarea, button')
    first?.focus()
  } else { previousFocus?.focus() }
})
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.stopPropagation(); if (!props.busy) emit('close') }
  if (event.key !== 'Tab') return
  const nodes = Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),a[href],[tabindex="0"]') || []).filter(el => el.offsetParent !== null)
  const first = nodes[0]; const last = nodes.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onBeforeUnmount(() => { if (props.open) previousFocus?.focus() })
</script>
<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="dialog-backdrop" :class="{ 'dialog-priority': priority }" @mousedown.self="!busy && emit('close')">
        <section ref="dialog" class="dialog-panel" :class="{ 'dialog-wide': wide }" role="dialog" aria-modal="true" :aria-labelledby="titleId" @keydown="keydown">
          <header class="dialog-header"><h2 :id="titleId">{{ title }}</h2><button class="icon-button" type="button" aria-label="关闭弹窗" :disabled="busy" @click="emit('close')"><Icon name="close" /></button></header>
          <slot />
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
