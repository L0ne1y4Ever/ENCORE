import { Client } from '@stomp/stompjs'
import { resolveWebSocketUrl } from './realtime'
import type { RealtimeConnectionState } from './realtime'

export interface DashboardRefreshEvent {
  reason: string
  referenceId: string
  timestamp: string
}

interface DashboardRealtimeHandlers {
  onEvent: (event: DashboardRefreshEvent) => void
  onStateChange?: (state: RealtimeConnectionState) => void
}

export function subscribeToDashboardUpdates(handlers: DashboardRealtimeHandlers): () => void {
  const client = new Client({
    brokerURL: resolveWebSocketUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })

  client.beforeConnect = async () => {
    handlers.onStateChange?.('connecting')
  }

  client.onConnect = () => {
    handlers.onStateChange?.('connected')
    client.subscribe('/topic/admin/dashboard', (message) => {
      try {
        handlers.onEvent(JSON.parse(message.body) as DashboardRefreshEvent)
      } catch {
        // Ignore malformed realtime payloads and keep manual refresh available.
      }
    })
  }

  client.onStompError = () => {
    handlers.onStateChange?.('disconnected')
  }

  client.onWebSocketClose = () => {
    handlers.onStateChange?.('disconnected')
  }

  client.activate()

  return () => {
    void client.deactivate()
  }
}
