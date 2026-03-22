import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function clearCookies() {
  document.cookie.split(';').forEach((cookie) => {
    const [name] = cookie.split('=')
    const trimmedName = name?.trim()

    if (trimmedName) {
      document.cookie = `${trimmedName}=; path=/; max-age=0`
    }
  })
}

describe('apiClient', () => {
  beforeEach(() => {
    clearCookies()
    vi.resetModules()
  })

  afterEach(() => {
    clearCookies()
  })

  it('attaches the bearer token from cookie storage to outgoing requests', async () => {
    document.cookie = 'west_flow_ai_access_token=cookie-token; path=/'

    const { apiClient } = await import('./client')
    const adapter = vi.fn(
      async (config: InternalAxiosRequestConfig): Promise<{
        data: { code: string; message: string; data: null; requestId: string }
        status: number
        statusText: string
        headers: Record<string, string>
        config: InternalAxiosRequestConfig
      }> => ({
        data: {
          code: 'OK',
          message: 'success',
          data: null,
          requestId: 'req_001',
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    )

    apiClient.defaults.adapter = adapter
    await apiClient.get('/auth/current-user')

    expect(adapter).toHaveBeenCalledTimes(1)
    const [config] = adapter.mock.calls[0] as [InternalAxiosRequestConfig]

    expect(
      config.headers.Authorization ?? config.headers.get?.('Authorization')
    ).toBe('Bearer cookie-token')
  })
})
