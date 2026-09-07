<!-- 文件作用：提供登录后页面共用的响应式侧栏、顶栏和内容容器。 -->
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const activePath = computed(() => route.path)
const canViewUsers = computed(() => authStore.hasPermission('system:user:list'))
const canViewRoles = computed(() => authStore.hasPermission('system:role:list'))

const handleLogout = async () => {
    await authStore.logout()
    await router.replace('/login')
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside
      :width="collapsed ? '72px' : '224px'"
      class="sidebar"
    >
      <div
        class="brand"
        :class="{compact: collapsed}"
      >
        <span class="brand-mark">S</span>
        <span v-if="!collapsed">SaaS Portal</span>
      </div>
      <el-menu
        :collapse="collapsed"
        :default-active="activePath"
        router
        class="nav-menu"
      >
        <el-menu-item index="/dashboard">
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu
          v-if="canViewUsers || canViewRoles"
          index="system"
        >
          <template #title>
            <span>系统管理</span>
          </template>
          <el-menu-item
            v-if="canViewUsers"
            index="/system/users"
          >
            用户管理
          </el-menu-item>
          <el-menu-item
            v-if="canViewRoles"
            index="/system/roles"
          >
            角色管理
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-button
          text
          @click="collapsed = !collapsed"
        >
          {{ collapsed ? '展开' : '收起' }}
        </el-button>
        <div class="account">
          <span>{{ authStore.displayName }}</span>
          <el-button
            text
            type="primary"
            @click="handleLogout"
          >
            退出登录
          </el-button>
        </div>
      </el-header>
      <el-main class="page-container">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell { min-height: 100vh; background: #f3f6fb; }
.sidebar { background: #10233f; transition: width .2s; overflow: hidden; }
.brand { height: 64px; display: flex; align-items: center; gap: 10px; padding: 0 18px; color: white; font-size: 18px; font-weight: 700; white-space: nowrap; }
.brand.compact { justify-content: center; padding: 0; }
.brand-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; color: #10233f; background: #7dd3fc; }
.nav-menu { border-right: 0; background: transparent; --el-menu-text-color: #bfd0e5; --el-menu-hover-bg-color: #1b365d; --el-menu-active-color: #7dd3fc; }
.topbar { display: flex; align-items: center; justify-content: space-between; height: 64px; background: white; border-bottom: 1px solid #e5eaf2; }
.account { display: flex; align-items: center; gap: 12px; color: #475569; }
.page-container { padding: 22px; }
@media (max-width: 720px) { .page-container { padding: 12px; } .account > span { display: none; } }
</style>
