/** 文件作用：定义登录令牌和当前认证用户的前端契约。 */
export interface LoginCommand {
    tenantCode: string
    username: string
    password: string
}
export interface TokenResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    expiresIn: number
}

export interface CurrentUser {
    tenantId: string
    userId: string
    username: string
    displayName: string
    permissions: string[]
}
