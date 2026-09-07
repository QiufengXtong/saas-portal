/** 文件作用：提供管理页面复用的日期时间安全展示格式。 */
export const formatDateTime = (value: string | null): string => {
    if (!value) return '-'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', {hour12: false})
}
