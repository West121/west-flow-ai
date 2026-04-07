import { CurrentUser, LoginRequest, LoginResponse } from '../../types/auth'
import { requestApi } from './client'

export function login(payload: LoginRequest) {
  return requestApi<LoginResponse>({
    path: '/auth/login',
    method: 'POST',
    data: payload,
  })
}

export function getCurrentUser() {
  return requestApi<CurrentUser>({
    path: '/auth/current-user',
  })
}
