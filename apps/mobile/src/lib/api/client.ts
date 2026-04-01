import axios, { AxiosError } from 'axios'
import { useAuthStore } from '@/stores/auth-store'

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

const apiBaseUrl =
  process.env.EXPO_PUBLIC_API_BASE_URL?.trim() ||
  'http://127.0.0.1:8080/api/v1'

export function resolveApiUrl(path: string) {
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  const base = apiBaseUrl.replace(/\/+$/g, '')
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${base}${normalized}`
}

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 20_000,
})

apiClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  const isFormData =
    typeof FormData !== 'undefined' && config.data instanceof FormData

  if (isFormData) {
    delete config.headers['Content-Type']
  } else if (
    config.data !== undefined &&
    config.data !== null &&
    !config.headers['Content-Type']
  ) {
    config.headers['Content-Type'] = 'application/json'
  }

  return config
})

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

export function getApiErrorResponse(
  error: unknown
): ApiErrorResponse | undefined {
  if (error instanceof AxiosError && isApiErrorResponse(error.response?.data)) {
    return error.response.data
  }
  return undefined
}

export function getApiErrorMessage(
  error: unknown,
  fallback = '操作失败，请稍后重试。'
) {
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

export function unwrapResponse<T>(response: { data: ApiSuccessResponse<T> }) {
  return response.data.data
}
