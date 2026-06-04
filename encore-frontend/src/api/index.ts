import axios from 'axios'
import type { AxiosResponse } from 'axios'

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

const TOKEN_NAME_KEY = 'encore.tokenName'
const TOKEN_VALUE_KEY = 'encore.tokenValue'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
  withCredentials: true
})

apiClient.interceptors.request.use((config) => {
  const tokenName = sessionStorage.getItem(TOKEN_NAME_KEY)
  const tokenValue = sessionStorage.getItem(TOKEN_VALUE_KEY)
  if (tokenName && tokenValue) {
    if (config.headers && typeof config.headers.set === 'function') {
      config.headers.set(tokenName, tokenValue)
    } else {
      config.headers = {
        ...config.headers,
        [tokenName]: tokenValue
      } as typeof config.headers
    }
  }
  return config
})

export async function requestData<T>(request: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  try {
    const response = await request
    const body = response.data
    if (body.code !== 0) {
      throw new Error(body.msg || 'Request failed')
    }
    return body.data
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      const message = error.response?.data?.msg
      if (message) {
        throw new Error(message)
      }
    }
    throw error
  }
}

export function saveAuthToken(tokenName: string, tokenValue: string) {
  sessionStorage.setItem(TOKEN_NAME_KEY, tokenName)
  sessionStorage.setItem(TOKEN_VALUE_KEY, tokenValue)
}

export function clearAuthToken() {
  sessionStorage.removeItem(TOKEN_NAME_KEY)
  sessionStorage.removeItem(TOKEN_VALUE_KEY)
}
