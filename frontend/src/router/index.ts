/** 文件作用：集中声明 IAM 页面路由，并执行登录恢复和页面权限守卫。 */
import {createRouter, createWebHistory} from 'vue-router'

import {useAuthStore} from '@/stores/auth'

declare module 'vue-router' {
    interface RouteMeta {
        title: string
        public?: boolean
        permission?: string
    }
}

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: {title: '登录', public: true}},
        {
            path: '/', component: () => import('@/layouts/AdminLayout.vue'), meta: {title: '管理后台'},
            children: [
                {path: '', redirect: '/dashboard'},
                {path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: {title: '工作台'}},
                {path: 'system/users', name: 'users', component: () => import('@/views/system/user/UserListView.vue'), meta: {title: '用户管理', permission: 'system:user:list'}},
                {path: 'system/roles', name: 'roles', component: () => import('@/views/system/role/RoleListView.vue'), meta: {title: '角色管理', permission: 'system:role:list'}},
            ],
        },
        {path: '/403', name: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: {title: '无权访问'}},
        {path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: {title: '页面不存在', public: true}},
    ],
})

router.beforeEach(async (to) => {
    document.title = `${to.meta.title} - SaaS Portal`
    const authStore = useAuthStore()
    if (to.meta.public) {
        if (to.name === 'login' && await authStore.restore()) return '/dashboard'
        return true
    }
    if (!await authStore.restore()) return {name: 'login', query: {redirect: to.fullPath}}
    if (!authStore.hasPermission(to.meta.permission)) return {name: 'forbidden'}
    return true
})

export default router
