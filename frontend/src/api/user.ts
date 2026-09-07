/** 文件作用：封装租户用户查询、维护、密码和角色分配接口。 */
import type {PageResult} from '@/types/api'
import type {CreateUserCommand, UpdateUserCommand, UserQuery, UserView} from '@/types/system'
import {apiRequest} from '@/utils/request'

export const getUsers = (params: UserQuery): Promise<PageResult<UserView>> => apiRequest({method: 'GET', url: '/api/v1/system/users', params})
export const getUser = (id: string): Promise<UserView> => apiRequest({method: 'GET', url: `/api/v1/system/users/${id}`})
export const createUser = (data: CreateUserCommand): Promise<string> => apiRequest({method: 'POST', url: '/api/v1/system/users', data})
export const updateUser = (id: string, data: UpdateUserCommand): Promise<void> => apiRequest({method: 'PUT', url: `/api/v1/system/users/${id}`, data})
export const enableUser = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/users/${id}/enable`})
export const disableUser = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/users/${id}/disable`})
export const resetUserPassword = (id: string, password: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/users/${id}/reset-password`, data: {password}})
export const deleteUser = (id: string): Promise<void> => apiRequest({method: 'DELETE', url: `/api/v1/system/users/${id}`})
export const assignUserRoles = (id: string, roleIds: string[]): Promise<void> => apiRequest({method: 'PUT', url: `/api/v1/system/users/${id}/roles`, data: {roleIds}})
