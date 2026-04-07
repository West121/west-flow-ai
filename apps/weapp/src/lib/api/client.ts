import Taro from '@tarojs/taro'
import { useAuthStore } from '../../stores/auth-store'

declare const __API_BASE_URL__: string

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

const apiBaseUrl = __API_BASE_URL__ || 'http://127.0.0.1:8080/api/v1'

export function resolveApiUrl(path: string) {
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  const base = apiBaseUrl.replace(/\/+$/g, '')
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${base}${normalized}`
}

function buildHeaders(extra?: Record<string, string>) {
  const accessToken = useAuthStore.getState().accessToken
  return {
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    ...extra,
  }
}

function isApiError(value: unknown): value is ApiErrorResponse {
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

export function getApiErrorMessage(error: unknown, fallback = '操作失败，请稍后重试。') {
  if (isApiError(error)) {
    return error.fieldErrors?.[0]?.message || error.message || fallback
  }
  if (error instanceof Error) {
    return error.message || fallback
  }
  return fallback
}

export async function requestApi<T>(options: {
  path: string
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  data?: unknown
}) {
  const url = resolveApiUrl(options.path)
  console.log('[weapp/api] request:start', {
    method: options.method ?? 'GET',
    path: options.path,
    url,
    hasToken: Boolean(useAuthStore.getState().accessToken),
  })
  const response = await Taro.request<ApiSuccessResponse<T> | ApiErrorResponse>({
    url,
    method: options.method ?? 'GET',
    data: options.data,
    timeout: 20_000,
    header: buildHeaders({
      'Content-Type': 'application/json',
    }),
  })

  if (
    response.statusCode >= 200 &&
    response.statusCode < 300 &&
    response.data &&
    'code' in response.data &&
    response.data.code === 'OK'
  ) {
    console.log('[weapp/api] request:success', {
      method: options.method ?? 'GET',
      path: options.path,
      statusCode: response.statusCode,
    })
    return (response.data as ApiSuccessResponse<T>).data
  }

  console.error('[weapp/api] request:error', {
    method: options.method ?? 'GET',
    path: options.path,
    statusCode: response.statusCode,
    response: response.data,
  })
  throw (isApiError(response.data)
    ? response.data
    : new Error(`请求失败（${response.statusCode}）`))
}

export async function uploadApiFile<T>(options: {
  path: string
  filePath: string
  name: string
  mimeType?: string
  formData?: Record<string, string>
}) {
  const response = await Taro.uploadFile({
    url: resolveApiUrl(options.path),
    filePath: options.filePath,
    name: 'file',
    timeout: 30_000,
    header: buildHeaders(
      options.mimeType ? { 'Content-Type': options.mimeType } : undefined
    ),
    formData: {
      displayName: options.name,
      ...(options.formData ?? {}),
    },
  })

  const payload = JSON.parse(response.data) as ApiSuccessResponse<T> | ApiErrorResponse
  if (response.statusCode >= 200 && response.statusCode < 300 && payload.code === 'OK') {
    return (payload as ApiSuccessResponse<T>).data
  }
  throw (isApiError(payload) ? payload : new Error(`上传失败（${response.statusCode}）`))
}
