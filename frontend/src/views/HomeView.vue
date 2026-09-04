<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'

import {getHealth} from '@/api/health'
import type {HealthResponse, HealthStatus} from '@/types/health'

type TagType = 'success' | 'warning' | 'info' | 'danger'

const loading = ref(false)
const health = ref<HealthResponse | null>(null)
const isReachable = ref(true)

const applicationStatus = computed<HealthStatus>(() => {
  if (!isReachable.value) return 'UNREACHABLE'
  return health.value?.status ?? 'UNKNOWN'
})

const componentStatus = (name: string): HealthStatus => {
  return health.value?.components?.[name]?.status ?? 'UNKNOWN'
}

const tagType = (status: HealthStatus): TagType => {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  if (status === 'UNREACHABLE') return 'warning'
  return 'info'
}

const loadHealth = async () => {
  loading.value = true

  try {
    health.value = await getHealth()
    isReachable.value = true
  } catch {
    health.value = null
    isReachable.value = false
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
</script>

<template>
  <main class="home-page">
    <el-card
        class="status-card"
        shadow="never"
    >
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">
              SAAS PORTAL
            </p>
            <h1>项目骨架已启动</h1>
            <p class="subtitle">
              基础服务连接状态
            </p>
          </div>
          <el-button
              :loading="loading"
              type="primary"
              @click="loadHealth"
          >
            刷新状态
          </el-button>
        </div>
      </template>

      <div
          aria-live="polite"
          class="status-list"
      >
        <div class="status-row">
          <span>后端应用</span>
          <el-tag :type="tagType(applicationStatus)">
            {{ applicationStatus }}
          </el-tag>
        </div>
        <div class="status-row">
          <span>MySQL</span>
          <el-tag :type="tagType(componentStatus('db'))">
            {{ componentStatus('db') }}
          </el-tag>
        </div>
        <div class="status-row">
          <span>Redis</span>
          <el-tag :type="tagType(componentStatus('redis'))">
            {{ componentStatus('redis') }}
          </el-tag>
        </div>
      </div>

      <el-alert
          v-if="!isReachable"
          :closable="false"
          class="status-alert"
          show-icon
          title="无法连接后端服务，请确认后端已在 8080 端口启动。"
          type="warning"
      />
    </el-card>
  </main>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.status-card {
  width: min(100%, 620px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}

.status-list {
  display: grid;
  gap: 12px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.status-alert {
  margin-top: 16px;
}

@media (max-width: 560px) {
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-header .el-button {
    width: 100%;
  }
}
</style>
