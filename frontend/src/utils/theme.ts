/** 文件作用：管理明亮/暗黑主题的初始化、持久化与切换。 */
import {computed, ref} from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'saas-portal-theme'
const theme = ref<ThemeMode>('light')

const applyTheme = (mode: ThemeMode): void => {
    theme.value = mode
    document.documentElement.classList.toggle('dark', mode === 'dark')
    document.documentElement.dataset.theme = mode
    document.documentElement.style.colorScheme = mode
}

/** 根据持久化配置或系统偏好初始化页面主题。 */
export const initializeTheme = (): void => {
    const stored = localStorage.getItem(STORAGE_KEY)
    const preferred = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    applyTheme(stored === 'light' || stored === 'dark' ? stored : preferred)
}

/** 提供当前主题状态和用户主动切换能力。 */
export const useTheme = () => {
    const isDark = computed(() => theme.value === 'dark')
    const toggleTheme = (): void => {
        const next: ThemeMode = isDark.value ? 'light' : 'dark'
        localStorage.setItem(STORAGE_KEY, next)
        applyTheme(next)
    }
    return {theme, isDark, toggleTheme}
}
