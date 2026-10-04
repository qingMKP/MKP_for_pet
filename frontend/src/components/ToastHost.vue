<script setup>
import { toasts } from '../utils/toast'
import AppIcon from './AppIcon.vue'

const ICON = { info: 'check', success: 'check', error: 'x' }
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="toast"
      :class="`toast--${t.type}`"
    >
      <AppIcon :name="ICON[t.type] || 'check'" :size="15" />
      <span>{{ t.message }}</span>
    </div>
  </div>
</template>
