/**
 * 使用 `document.cookie` 手工实现的 Cookie 工具函数。
 * 这样可以避免引入 `js-cookie`，保持行为更可控。
 */

const DEFAULT_MAX_AGE = 60 * 60 * 24 * 7 // 7 天

/**
 * 按名称读取 Cookie 值。
 */
export function getCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined

  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) {
    const cookieValue = parts.pop()?.split(';').shift()
    return cookieValue
  }
  return undefined
}

/**
 * 写入 Cookie，可选指定最大存活时间。
 */
export function setCookie(
  name: string,
  value: string,
  maxAge: number = DEFAULT_MAX_AGE
): void {
  if (typeof document === 'undefined') return

  document.cookie = `${name}=${value}; path=/; max-age=${maxAge}`
}

/**
 * 通过把最大存活时间设为 0 来删除 Cookie。
 */
export function removeCookie(name: string): void {
  if (typeof document === 'undefined') return

  document.cookie = `${name}=; path=/; max-age=0`
}
