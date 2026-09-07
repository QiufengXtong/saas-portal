/** 文件作用：集中管理当前标签页的认证用户、令牌生命周期和权限判断。 */
import {defineStore} from 'pinia'
import {computed, ref} from 'vue'

import * as authApi from '@/api/auth'
import type {CurrentUser, LoginCommand} from '@/types/auth'
import {clearAuthTokens, readAuthTokens, saveAuthTokens} from '@/utils/authSession'

export const useAuthStore = defineStore('auth', () => {
    const currentUser = ref<CurrentUser | null>(null)
    const initialized = ref(false)
    const authenticated = computed(() => currentUser.value !== null)
    const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || '')
    const hasPermission = (permission?: string): boolean => !permission || Boolean(currentUser.value?.permissions.includes(permission))

    const loadCurrentUser = async (): Promise<void> => {
        currentUser.value = await authApi.getCurrentUser()
        initialized.value = true
    }

    const restore = async (): Promise<boolean> => {
        if (initialized.value) return authenticated.value
        if (!readAuthTokens()) {
            initialized.value = true
            return false
        }
        try {
            await loadCurrentUser()
            return true
        } catch {
            clearAuthTokens()
            currentUser.value = null
            initialized.value = true
            return false
        }
    }

    const login = async (command: LoginCommand): Promise<void> => {
        const tokens = await authApi.login(command)
        saveAuthTokens(tokens)
        try {
            await loadCurrentUser()
        } catch (error) {
            clearAuthTokens()
            throw error
        }
    }

    const logout = async (): Promise<void> => {
        try {
            if (readAuthTokens()) await authApi.logout()
        } catch {
            // 请求层已经提示失败，本地会话仍必须清除。
        } finally {
            clearAuthTokens()
            currentUser.value = null
            initialized.value = true
        }
    }

    return {currentUser, initialized, authenticated, displayName, hasPermission, restore, login, logout}
})
