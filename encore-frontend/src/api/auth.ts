import { apiClient, clearAuthToken, requestData, saveAuthToken } from './index'

export type UserRole = 'user' | 'admin' | 'checker' | 'sysadmin'

export interface UserProfile {
  id: string
  username: string
  role: UserRole
  displayName: string
}

interface LoginResponse {
  tokenName: string
  tokenValue: string
  user: UserProfile
}

export async function loginApi(username: string, password: string): Promise<UserProfile> {
  const response = await requestData<LoginResponse>(
    apiClient.post('/api/auth/login', { username, password })
  )
  saveAuthToken(response.tokenName, response.tokenValue)
  return response.user
}

export async function logoutApi(): Promise<void> {
  try {
    await requestData<void>(apiClient.post('/api/auth/logout'))
  } finally {
    clearAuthToken()
  }
}

export function getCurrentUserApi(): Promise<UserProfile> {
  return requestData<UserProfile>(apiClient.get('/api/auth/me'))
}
