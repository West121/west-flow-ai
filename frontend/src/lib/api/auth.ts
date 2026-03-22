import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  SwitchContextRequest,
} from '@/features/shared/auth/types'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<ApiSuccessResponse<LoginResponse>>(
    '/auth/login',
    payload
  )

  return unwrapResponse(response)
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response =
    await apiClient.get<ApiSuccessResponse<CurrentUser>>('/auth/current-user')

  return unwrapResponse(response)
}

export async function switchContext(
  payload: SwitchContextRequest
): Promise<CurrentUser> {
  await apiClient.post<ApiSuccessResponse<null>>('/auth/switch-context', payload)

  return getCurrentUser()
}
