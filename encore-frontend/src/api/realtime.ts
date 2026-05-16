import { apiClient } from './index'

export type RealtimeConnectionState = 'connecting' | 'connected' | 'disconnected'

export const resolveWebSocketUrl = () => {
  const baseUrl = new URL(apiClient.defaults.baseURL || window.location.origin, window.location.origin)
  baseUrl.protocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  baseUrl.pathname = '/ws'
  baseUrl.search = ''
  baseUrl.hash = ''
  return baseUrl.toString()
}
