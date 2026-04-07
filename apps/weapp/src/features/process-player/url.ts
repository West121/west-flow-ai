declare const __WEB_BASE_URL__: string
declare const __PROCESS_PLAYER_BASE_URL__: string

export function buildProcessPlayerUrl(ticket: string) {
  const base =
    __PROCESS_PLAYER_BASE_URL__ ||
    __WEB_BASE_URL__ ||
    'http://127.0.0.1:5173'

  const normalized = base.replace(/\/+$/g, '')
  return `${normalized}/review/weapp/${encodeURIComponent(ticket)}?source=weapp`
}
