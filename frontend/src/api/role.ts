/** 文件作用：封装租户角色查询、维护和菜单授权接口。 */
import type {PageResult} from '@/types/api'
import type {RoleQuery, RoleView} from '@/types/system'
import {apiRequest} from '@/utils/request'

export const getRoles = (params: RoleQuery): Promise<PageResult<RoleView>> => apiRequest({method: 'GET', url: '/api/v1/system/roles', params})
export const getRole = (id: string): Promise<RoleView> => apiRequest({method: 'GET', url: `/api/v1/system/roles/${id}`})
export const getRoleMenuIds = (id: string): Promise<string[]> => apiRequest({method: 'GET', url: `/api/v1/system/roles/${id}/menus`})
export const createRole = (roleCode: string, roleName: string): Promise<string> => apiRequest({method: 'POST', url: '/api/v1/system/roles', data: {roleCode, roleName}})
export const updateRole = (id: string, roleName: string): Promise<void> => apiRequest({method: 'PUT', url: `/api/v1/system/roles/${id}`, data: {roleName}})
export const enableRole = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/roles/${id}/enable`})
export const disableRole = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/roles/${id}/disable`})
export const deleteRole = (id: string): Promise<void> => apiRequest({method: 'DELETE', url: `/api/v1/system/roles/${id}`})
export const assignRoleMenus = (id: string, menuIds: string[]): Promise<void> => apiRequest({method: 'PUT', url: `/api/v1/system/roles/${id}/menus`, data: {menuIds}})
