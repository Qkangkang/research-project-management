/**
 * 日期时间格式化工具（时区：Asia/Shanghai）
 */

/**
 * 格式化日期
 * @param iso ISO 8601 字符串或 Date 对象
 * @returns "2026-11-18" 格式
 */
export function formatDate(iso: string | Date | null | undefined): string {
  if (!iso) {
    return ''
  }

  const date = typeof iso === 'string' ? new Date(iso) : iso

  if (isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

/**
 * 格式化日期时间
 * @param iso ISO 8601 字符串或 Date 对象
 * @returns "2026-11-18 10:30:00" 格式
 */
export function formatDateTime(iso: string | Date | null | undefined): string {
  if (!iso) {
    return ''
  }

  const date = typeof iso === 'string' ? new Date(iso) : iso

  if (isNaN(date.getTime())) {
    return ''
  }

  const datePart = formatDate(date)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  return `${datePart} ${hours}:${minutes}:${seconds}`
}

/**
 * 格式化相对时间（如 "3 分钟前"）
 */
export function formatRelativeTime(iso: string | Date | null | undefined): string {
  if (!iso) {
    return ''
  }

  const date = typeof iso === 'string' ? new Date(iso) : iso
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSec = Math.floor(diffMs / 1000)

  if (diffSec < 60) {
    return '刚刚'
  } else if (diffSec < 3600) {
    return `${Math.floor(diffSec / 60)} 分钟前`
  } else if (diffSec < 86400) {
    return `${Math.floor(diffSec / 3600)} 小时前`
  } else if (diffSec < 2592000) {
    return `${Math.floor(diffSec / 86400)} 天前`
  } else {
    return formatDate(date)
  }
}

/**
 * 解析日期字符串为 Date 对象
 */
export function parseDate(dateStr: string): Date | null {
  if (!dateStr) {
    return null
  }

  const date = new Date(dateStr)
  return isNaN(date.getTime()) ? null : date
}
