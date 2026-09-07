/** 文件作用：统一处理 API 响应、Bearer Token、401 单飞刷新和安全错误提示。 */
import axios, {AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig} from 'axios'
import {ElMessage} from 'element-plus'

import type {ApiResult} from '@/types/api'
import type {TokenResponse} from '@/types/auth'
import {clearAuthTokens, readAuthTokens, saveAuthTokens} from '@/utils/authSession'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
    _retriedAfterRefresh?: boolean
}

const request = axios.create({baseURL: '/', timeout: 10000})
const refreshClient = axios.create({baseURL: '/', timeout: 10000})
let refreshPromise: Promise<string> | null = null

const refreshAccessToken = async (): Promise<string> => {
    const tokens = readAuthTokens()
    if (!tokens?.refreshToken) throw new Error('Missing refresh token')
    const response = await refreshClient.post<ApiResult<TokenResponse>>(
        '/api/v1/auth/refresh', {refreshToken: tokens.refreshToken})
    if (response.data.code !== 0 || !response.data.data?.accessToken) {
        throw new Error(response.data.message || '登录状态已失效')
    }
    saveAuthTokens(response.data.data)
    return response.data.data.accessToken
}

const redirectToLogin = (): void => {
    clearAuthTokens()
    if (window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
    }
}

request.interceptors.request.use((config) => {
    const accessToken = readAuthTokens()?.accessToken
    if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
    return config
})

request.interceptors.response.use(
    response => response,
    async (error: AxiosError<ApiResult<unknown>>) => {
        const config = error.config as RetryableRequestConfig | undefined
        const isAuthEndpoint = config?.url?.startsWith('/api/v1/auth/login')
            || config?.url?.startsWith('/api/v1/auth/refresh')
        if (error.response?.status === 401 && config && !config._retriedAfterRefresh && !isAuthEndpoint) {
            config._retriedAfterRefresh = true
            try {
                refreshPromise ??= refreshAccessToken().finally(() => { refreshPromise = null })
                const accessToken = await refreshPromise
                config.headers.Authorization = `Bearer ${accessToken}`
                return request(config)
            } catch {
                redirectToLogin()
                return Promise.reject(error)
            }
        }
        const message = error.response?.data?.message
            || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '请求失败，请检查网络或服务状态')
        ElMessage.error(message)
        return Promise.reject(error)
    },
)

export const apiRequest = async <T>(config: AxiosRequestConfig): Promise<T> => {
    const response = await request.request<ApiResult<T>>(config)
    if (response.data.code !== 0) {
        ElMessage.error(response.data.message || '操作失败')
        throw new Error(response.data.message || 'Business request failed')
    }
    return response.data.data
}

export default request
