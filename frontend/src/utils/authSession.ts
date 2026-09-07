/** 文件作用：在当前浏览器标签页内集中保存和读取认证令牌。 */
import type {TokenResponse} from '@/types/auth'

const STORAGE_KEY = 'saas-portal.auth-tokens'

export interface AuthTokens {
    accessToken: string
    refreshToken: string
}
export const readAuthTokens = (): AuthTokens | null => {
    const serialized = sessionStorage.getItem(STORAGE_KEY)
    if (!serialized) return null
    try {
        const value: unknown = JSON.parse(serialized)
        if (typeof value === 'object' && value !== null
            && 'accessToken' in value && typeof value.accessToken === 'string'
            && 'refreshToken' in value && typeof value.refreshToken === 'string') {
            return {accessToken: value.accessToken, refreshToken: value.refreshToken}
        }
    } catch {
        // 损坏的会话数据按未登录处理。
    }
    sessionStorage.removeItem(STORAGE_KEY)
    return null
}

export const saveAuthTokens = (tokens: TokenResponse | AuthTokens): void => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
    }))
}

export const clearAuthTokens = (): void => {
    sessionStorage.removeItem(STORAGE_KEY)
}
