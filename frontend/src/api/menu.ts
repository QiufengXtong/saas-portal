/** 文件作用：封装全局菜单树和权限目录只读接口。 */
import type {MenuTreeNode} from '@/types/system'
import {apiRequest} from '@/utils/request'

export const getMenuTree = (): Promise<MenuTreeNode[]> => apiRequest({method: 'GET', url: '/api/v1/system/menus/tree'})
export const getPermissionCodes = (): Promise<string[]> => apiRequest({method: 'GET', url: '/api/v1/system/menus/permissions'})
