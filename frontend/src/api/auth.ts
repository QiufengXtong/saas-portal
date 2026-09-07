/** 文件作用：封装登录、退出和当前用户认证接口。 */
import type {CurrentUser, LoginCommand, TokenResponse} from '@/types/auth'
import {apiRequest} from '@/utils/request'

export const login = (command: LoginCommand): Promise<TokenResponse> =>
    apiRequest({method: 'POST', url: '/api/v1/auth/login', data: command})

export const logout = (): Promise<void> =>
    apiRequest({method: 'POST', url: '/api/v1/auth/logout'})

export const getCurrentUser = (): Promise<CurrentUser> =>
    apiRequest({method: 'GET', url: '/api/v1/auth/me'})
