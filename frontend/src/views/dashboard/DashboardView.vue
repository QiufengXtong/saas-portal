<!-- 文件作用：展示当前租户、用户和权限数量的后台工作台概览。 -->
<script setup lang="ts">
import {computed} from 'vue'
import {useAuthStore} from '@/stores/auth'

const authStore = useAuthStore()
const permissionCount = computed(() => authStore.currentUser?.permissions.length ?? 0)
</script>

<template>
  <section>
    <div class="welcome">
      <p>欢迎回来</p>
      <h1>{{ authStore.displayName }}</h1>
      <span>系统已根据当前会话加载最新权限。</span>
    </div>
    <div class="metrics">
      <el-card shadow="never">
        <span>当前租户</span><strong>{{ authStore.currentUser?.tenantId }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>当前用户</span><strong>{{ authStore.currentUser?.username }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>有效权限</span><strong>{{ permissionCount }}</strong>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.welcome { padding: 34px; border: 1px solid rgb(255 255 255 / 10%); border-radius: 18px; color: white; background: var(--dashboard-gradient); box-shadow: var(--dashboard-shadow); }
.welcome p, .welcome h1 { margin: 0; }.welcome h1 { margin: 8px 0; font-size: 34px; }.welcome span { color: #d7e8fa; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 20px; }.metrics :deep(.el-card) { border-color: var(--app-border); background: var(--app-surface); }.metrics span { display: block; color: var(--app-text-secondary); }.metrics strong { display: block; margin-top: 12px; color: var(--app-text); font-size: 24px; }
@media (max-width: 760px) { .metrics { grid-template-columns: 1fr; } }
</style>
