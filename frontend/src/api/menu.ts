/** 文件作用：封装全局菜单树、权限目录和菜单权限资源维护接口。 */
import type {MenuCommand, MenuTreeNode, MenuView} from '@/types/system'
import {apiRequest} from '@/utils/request'

export const getMenuTree = (): Promise<MenuTreeNode[]> => apiRequest({method: 'GET', url: '/api/v1/system/menus/tree'})
export const getPermissionCodes = (): Promise<string[]> => apiRequest({method: 'GET', url: '/api/v1/system/menus/permissions'})
export const getManagementMenuTree = (): Promise<MenuTreeNode[]> => apiRequest({method: 'GET', url: '/api/v1/system/menus/management-tree'})
export const getMenu = (id: string): Promise<MenuView> => apiRequest({method: 'GET', url: `/api/v1/system/menus/${id}`})
export const createMenu = (command: MenuCommand): Promise<string> => apiRequest({method: 'POST', url: '/api/v1/system/menus', data: command})
export const updateMenu = (id: string, command: MenuCommand): Promise<void> => apiRequest({method: 'PUT', url: `/api/v1/system/menus/${id}`, data: command})
export const enableMenu = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/menus/${id}/enable`})
export const disableMenu = (id: string): Promise<void> => apiRequest({method: 'POST', url: `/api/v1/system/menus/${id}/disable`})
export const deleteMenu = (id: string): Promise<void> => apiRequest({method: 'DELETE', url: `/api/v1/system/menus/${id}`})
