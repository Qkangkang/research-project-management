/**
 * 金额格式化工具
 */

/**
 * 格式化金额（数字转字符串，千分位 + 两位小数）
 * @param value 数字金额
 * @returns "12,000.00" 格式
 */
export function formatMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '0.00'
  }

  const num = typeof value === 'string' ? parseFloat(value) : value

  if (isNaN(num)) {
    return '0.00'
  }

  return num.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/**
 * 解析金额字符串为数字
 * @param str 格式化的金额字符串（如 "12,000.00"）
 * @returns 数字
 */
export function parseMoney(str: string | null | undefined): number {
  if (!str) {
    return 0
  }

  // 移除千分位逗号
  const cleaned = str.replace(/,/g, '')
  const num = parseFloat(cleaned)

  return isNaN(num) ? 0 : num
}

/**
 * 金额加法（避免浮点精度问题）
 */
export function addMoney(a: number, b: number): number {
  return Math.round((a + b) * 100) / 100
}

/**
 * 金额减法
 */
export function subtractMoney(a: number, b: number): number {
  return Math.round((a - b) * 100) / 100
}

/**
 * 金额乘法
 */
export function multiplyMoney(a: number, b: number): number {
  return Math.round(a * b * 100) / 100
}
