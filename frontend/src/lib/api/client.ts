import axios, { AxiosError, type AxiosResponse } from 'axios'
import { AUTH_ACCESS_TOKEN_COOKIE } from '@/features/shared/auth/types'
import { getCookie } from '@/lib/cookies'

export type ApiSuccessResponse<T> = {
  code: 'OK'
  message: string
  data: T
  requestId: string
}

export type ApiFieldError = {
  field: string
  code: string
  message: string
}

export type ApiErrorResponse = {
  code: string
  message: string
  requestId: string
  path: string
  timestamp: string
  details?: Record<string, unknown>
  fieldErrors?: ApiFieldError[]
}

// 统一封装前端 API 客户端，负责基础地址、鉴权头和错误归一化。
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || '/api/v1'

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求前自动补上当前登录态的访问令牌。
apiClient.interceptors.request.use((config) => {
  const accessToken = getCookie(AUTH_ACCESS_TOKEN_COOKIE)

  if (accessToken) {
    if (typeof config.headers.set === 'function') {
      config.headers.set('Authorization', `Bearer ${accessToken}`)
    } else {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
  }

  return config
})

// 判断后端返回体是否符合统一错误结构，便于后续提取提示文案。
export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>

  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.requestId === 'string'
  )
}

// 从任意异常中提取接口错误详情，供页面直接展示。
export function getApiErrorResponse(
  error: unknown
): ApiErrorResponse | undefined {
  if (error instanceof AxiosError && isApiErrorResponse(error.response?.data)) {
    return error.response.data
  }

  return undefined
}

// 统一把异常转换成可展示的中文提示，保留接口返回的优先级。
export function getApiErrorMessage(
  error: unknown,
  fallback = '操作失败，请稍后重试。'
): string {
  const apiError = getApiErrorResponse(error)

  if (apiError) {
    return apiError.fieldErrors?.[0]?.message || apiError.message || fallback
  }

  if (error instanceof AxiosError) {
    return error.message || fallback
  }

  if (error instanceof Error) {
    return error.message || fallback
  }

  return fallback
}

// 把统一响应包裹层拆开，只返回真正的数据体。
export function unwrapResponse<T>(
  response: AxiosResponse<ApiSuccessResponse<T>>
): T {
  return response.data.data
}
