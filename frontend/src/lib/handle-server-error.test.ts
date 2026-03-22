import { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { handleServerError } from './handle-server-error'

const { errorToastMock } = vi.hoisted(() => ({
  errorToastMock: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: {
    error: errorToastMock,
  },
}))

function createAxiosError(data: unknown, status = 400) {
  return new AxiosError('Request failed', undefined, undefined, undefined, {
    data,
    status,
    statusText: 'Bad Request',
    headers: {},
    config: { headers: {} as never },
  })
}

describe('handleServerError', () => {
  afterEach(() => {
    errorToastMock.mockReset()
  })

  it('reads the frozen error-contract message from Axios responses', () => {
    handleServerError(
      createAxiosError({
        code: 'AUTH.FORBIDDEN',
        message: '无权限访问',
        requestId: 'req_001',
        path: '/api/v1/auth/current-user',
        timestamp: '2026-03-21T12:00:00+08:00',
      })
    )

    expect(errorToastMock).toHaveBeenCalledWith('无权限访问')
  })

  it('falls back to the first field error message for validation responses', () => {
    handleServerError(
      createAxiosError({
        code: 'VALIDATION.FIELD_INVALID',
        message: '请求参数校验失败',
        requestId: 'req_002',
        path: '/api/v1/auth/login',
        timestamp: '2026-03-21T12:00:00+08:00',
        fieldErrors: [
          {
            field: 'username',
            code: 'REQUIRED',
            message: '用户名不能为空',
          },
        ],
      })
    )

    expect(errorToastMock).toHaveBeenCalledWith('用户名不能为空')
  })
})
