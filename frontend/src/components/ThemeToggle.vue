<!-- 文件作用：提供全站复用的明亮/暗黑主题切换按钮。 -->
<script setup lang="ts">
import {useTheme} from '@/utils/theme'

defineProps<{compact?: boolean}>()
const {isDark, toggleTheme} = useTheme()
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="isDark ? '切换到明亮模式' : '切换到暗黑模式'"
    :title="isDark ? '切换到明亮模式' : '切换到暗黑模式'"
    @click="toggleTheme"
  >
    <span
      class="theme-icon"
      aria-hidden="true"
    >{{ isDark ? '☀' : '☾' }}</span>
    <span v-if="!compact">{{ isDark ? '明亮模式' : '暗黑模式' }}</span>
  </button>
</template>

<style scoped>
.theme-toggle { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-width: 38px; height: 36px; padding: 0 11px; color: var(--app-text-secondary); background: var(--app-surface-muted); border: 1px solid var(--app-border); border-radius: 10px; cursor: pointer; transition: color .2s, background-color .2s, border-color .2s, transform .2s; }
.theme-toggle:hover { color: var(--app-text); background: var(--app-hover); border-color: var(--app-border-strong); transform: translateY(-1px); }
.theme-toggle:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.theme-icon { width: 16px; font-size: 17px; line-height: 1; text-align: center; }
</style>
