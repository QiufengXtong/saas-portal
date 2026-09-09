<!-- 文件作用：提供登录后页面共用的响应式侧栏、顶栏和内容容器。 -->
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import ThemeToggle from '@/components/ThemeToggle.vue'
import {useAuthStore} from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const activePath = computed(() => route.path)
const canViewUsers = computed(() => authStore.hasPermission('system:user:list'))
const canViewRoles = computed(() => authStore.hasPermission('system:role:list'))
const canViewMenus = computed(() => authStore.hasPermission('system:platform:admin') && authStore.hasPermission('system:menu:list'))

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
          <svg
            class="menu-icon"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 13h6V4H4v9Zm0 7h6v-5H4v5Zm10 0h6v-9h-6v9Zm0-16v5h6V4h-6Z" /></svg>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu
          v-if="canViewUsers || canViewRoles || canViewMenus"
          index="system"
          popper-class="sidebar-menu-popper"
        >
          <template #title>
            <svg
              class="menu-icon"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><path d="M19.4 13a7.8 7.8 0 0 0 .05-1 7.8 7.8 0 0 0-.05-1l2.1-1.65-2-3.46-2.55 1.03a7.54 7.54 0 0 0-1.73-1L14.83 3h-4l-.39 2.92a7.54 7.54 0 0 0-1.73 1L6.16 5.89l-2 3.46L6.26 11a7.8 7.8 0 0 0-.05 1 7.8 7.8 0 0 0 .05 1l-2.1 1.65 2 3.46 2.55-1.03a7.54 7.54 0 0 0 1.73 1l.39 2.92h4l.39-2.92a7.54 7.54 0 0 0 1.73-1l2.55 1.03 2-3.46L19.4 13Zm-6.57 2.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7Z" /></svg>
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
          <el-menu-item
            v-if="canViewMenus"
            index="/system/menus"
          >
            菜单管理
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <button
          class="collapse-button"
          type="button"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="collapsed = !collapsed"
        >
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 6h16v2H4V6Zm0 5h10v2H4v-2Zm0 5h16v2H4v-2Z" /></svg>
          <span>{{ collapsed ? '展开' : '收起' }}</span>
        </button>
        <div class="account">
          <ThemeToggle compact />
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
.admin-shell { min-height: 100vh; color: var(--app-text); background: var(--app-bg); transition: background-color .25s, color .25s; }
.sidebar { background: var(--sidebar-bg); border-right: 1px solid var(--sidebar-border); transition: width .2s, background-color .25s, border-color .25s; overflow: hidden; }
.brand { height: 64px; display: flex; align-items: center; gap: 10px; padding: 0 18px; color: var(--sidebar-heading); font-size: 18px; font-weight: 700; white-space: nowrap; border-bottom: 1px solid var(--sidebar-border); }
.brand.compact { justify-content: center; padding: 0; }
.brand-mark { display: grid; flex: 0 0 auto; place-items: center; width: 34px; height: 34px; border-radius: 10px; color: white; background: linear-gradient(135deg, #409eff, #2563eb); box-shadow: 0 7px 18px rgb(64 158 255 / 28%); }
.nav-menu { padding-top: 10px; border-right: 0; background: transparent; --el-menu-bg-color: transparent; --el-menu-text-color: var(--sidebar-text); --el-menu-hover-bg-color: var(--sidebar-hover-bg); --el-menu-active-color: var(--sidebar-active-text); }
.menu-icon { width: 19px; height: 19px; margin-right: 10px; fill: currentcolor; }
:deep(.nav-menu.el-menu--collapse .menu-icon) { margin-right: 0; }
:deep(.nav-menu .el-menu-item), :deep(.nav-menu .el-sub-menu__title) { height: 46px; margin: 3px 10px; padding: 0 14px !important; border-radius: 9px; transition: color .18s, background-color .18s; }
:deep(.nav-menu .el-menu-item:hover), :deep(.nav-menu .el-sub-menu__title:hover) { color: var(--sidebar-hover-text) !important; background: var(--sidebar-hover-bg) !important; }
:deep(.nav-menu .el-menu-item.is-active) { color: var(--sidebar-active-text) !important; font-weight: 600; background: var(--sidebar-active-bg) !important; }
:deep(.nav-menu .el-sub-menu.is-active > .el-sub-menu__title) { color: var(--sidebar-active-text); }
:deep(.nav-menu .el-menu--inline) { background: transparent; }
:deep(.nav-menu .el-menu--inline .el-menu-item) { padding-left: 46px !important; }
.topbar { display: flex; align-items: center; justify-content: space-between; height: 64px; background: var(--app-header-bg); border-bottom: 1px solid var(--app-border); backdrop-filter: blur(12px); transition: background-color .25s, border-color .25s; }
.collapse-button { display: inline-flex; align-items: center; gap: 8px; height: 36px; padding: 0 10px; color: var(--app-text-secondary); background: transparent; border: 0; border-radius: 9px; cursor: pointer; }
.collapse-button:hover { color: var(--app-text); background: var(--app-hover); }
.collapse-button svg { width: 19px; height: 19px; fill: currentcolor; }
.account { display: flex; align-items: center; gap: 12px; color: var(--app-text-secondary); }
.page-container { padding: 22px; }
@media (max-width: 720px) { .page-container { padding: 12px; } .account > span, .collapse-button span { display: none; } }
</style>
