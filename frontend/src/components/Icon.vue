<script setup lang="ts">
import { Leaf, CalendarDays, Heart, ChevronLeft, ChevronRight, Plus, X, Trash2, LoaderCircle, Check, ImagePlus, UploadCloud, Ellipsis, Pencil, CircleAlert, Image, ArrowLeft, ArrowRight, RefreshCw } from 'lucide-vue-next'
const icons = { leaf: Leaf, calendar: CalendarDays, heart: Heart, left: ChevronLeft, right: ChevronRight, plus: Plus, close: X, trash: Trash2, loading: LoaderCircle, check: Check, imagePlus: ImagePlus, upload: UploadCloud, more: Ellipsis, edit: Pencil, alert: CircleAlert, image: Image, previous: ArrowLeft, next: ArrowRight, refresh: RefreshCw }
withDefaults(defineProps<{ name: keyof typeof icons; size?: number }>(), { size: 20 })
</script>
<template><component :is="icons[name]" :size="size" :stroke-width="1.7" aria-hidden="true" :class="{ spin: name === 'loading' }" /></template>
