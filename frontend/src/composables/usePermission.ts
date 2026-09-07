/** 文件作用：向页面和组件提供统一的当前用户权限判断能力。 */
import {useAuthStore} from '@/stores/auth'

export const usePermission = () => {
    const authStore = useAuthStore()
    return {hasPermission: authStore.hasPermission}
}
