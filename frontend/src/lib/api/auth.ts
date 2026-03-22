import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  SwitchContextRequest,
} from '@/features/shared/auth/types'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

// 登录接口返回令牌和用户信息，供前端初始化会话状态。
export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<ApiSuccessResponse<LoginResponse>>(
    '/auth/login',
    payload
  )

  return unwrapResponse(response)
}

// 拉取当前登录用户，通常用于刷新页面后的会话恢复。
export async function getCurrentUser(): Promise<CurrentUser> {
  const response =
    await apiClient.get<ApiSuccessResponse<CurrentUser>>('/auth/current-user')

  return unwrapResponse(response)
}

// 切换组织或上下文后，重新读取一次当前用户信息。
export async function switchContext(
  payload: SwitchContextRequest
): Promise<CurrentUser> {
  await apiClient.post<ApiSuccessResponse<null>>('/auth/switch-context', payload)

  return getCurrentUser()
}
