/** 文件作用：定义所有业务接口复用的统一响应与分页结构。 */
export interface ApiResult<T> {
    code: number
    message: string
    data: T
}
export interface PageResult<T> {
    records: T[]
    total: number
    pageNum: number
    pageSize: number
}
