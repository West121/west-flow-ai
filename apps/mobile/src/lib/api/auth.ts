import type { CurrentUser, LoginRequest, LoginResponse } from '@/types/auth'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from '@/lib/api/client'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<ApiSuccessResponse<LoginResponse>>(
    '/auth/login',
    payload
  )
  return unwrapResponse(response)
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await apiClient.get<ApiSuccessResponse<CurrentUser>>(
    '/auth/current-user'
  )
  return unwrapResponse(response)
}
