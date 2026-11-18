const TOKEN_KEY = 'rpm_access_token'
const TOKEN_EXPIRE_KEY = 'rpm_token_expire'

/**
 * 获取 Token
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 设置 Token
 */
export function setToken(token: string, expiresIn?: number): void {
  localStorage.setItem(TOKEN_KEY, token)

  if (expiresIn) {
    const expireTime = Date.now() + expiresIn * 1000
    localStorage.setItem(TOKEN_EXPIRE_KEY, expireTime.toString())
  }
}

/**
 * 移除 Token
 */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_EXPIRE_KEY)
}

/**
 * 检查 Token 是否过期
 */
export function isTokenExpired(): boolean {
  const expireTime = localStorage.getItem(TOKEN_EXPIRE_KEY)

  if (!expireTime) {
    return false
  }

  return Date.now() > parseInt(expireTime)
}
