/** 文件作用：定义用户、角色、菜单及其管理命令的前端接口契约。 */
export type EnabledStatus = 'ENABLED' | 'DISABLED'
export type MenuType = 'DIRECTORY' | 'MENU' | 'BUTTON'

export interface UserView {
    id: string
    tenantId: string
    username: string
    displayName: string
    email: string | null
    mobile: string | null
    status: EnabledStatus
    roleIds: string[]
    passwordChangedAt: string | null
    lastLoginAt: string | null
    createdAt: string | null
    updatedAt: string | null
}
export interface UserQuery {
    pageNum: number
    pageSize: number
    username?: string
    status?: EnabledStatus
}

export interface CreateUserCommand {
    username: string
    displayName: string
    password: string
    email?: string
    mobile?: string
    roleIds: string[]
}

export interface UpdateUserCommand {
    username?: string
    displayName: string
    email?: string
    mobile?: string
}

export interface RoleView {
    id: string
    tenantId: string
    roleCode: string
    roleName: string
    status: EnabledStatus
    builtIn: boolean
    createdAt: string | null
    updatedAt: string | null
}

export interface RoleQuery {
    pageNum: number
    pageSize: number
    roleCode?: string
    roleName?: string
    status?: EnabledStatus
}

export interface MenuTreeNode {
    id: string
    parentId: string | null
    name: string
    type: MenuType
    routePath: string | null
    component: string | null
    icon: string | null
    permissionCode: string | null
    sortOrder: number
    visible: boolean
    status: EnabledStatus
    builtIn: boolean
    children: MenuTreeNode[]
}

export interface MenuView extends Omit<MenuTreeNode, 'children'> {
    createdAt: string | null
    updatedAt: string | null
}

export interface MenuCommand {
    parentId: string | null
    name: string
    type: MenuType
    routePath: string | null
    component: string | null
    icon: string | null
    permissionCode: string | null
    sortOrder: number
    visible: boolean
}
