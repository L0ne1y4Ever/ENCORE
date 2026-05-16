import { Client } from '@stomp/stompjs'
import { resolveWebSocketUrl } from './realtime'
import type { RealtimeConnectionState } from './realtime'
import type { SeatStatus } from '../mock/seats'

export type SeatRealtimeConnectionState = RealtimeConnectionState

export interface SeatStatusChange {
  seatId: string
  status: SeatStatus
}

export interface SeatStatusEvent {
  scheduleId: string
  reason: 'LOCKED' | 'SOLD' | 'EXPIRED' | 'REFUNDED' | 'CANCELLED'
  timestamp: string
  seats: SeatStatusChange[]
}

interface SeatRealtimeHandlers {
  onEvent: (event: SeatStatusEvent) => void
  onStateChange?: (state: SeatRealtimeConnectionState) => void
}

export function subscribeToSeatUpdates(
  scheduleId: string,
  handlers: SeatRealtimeHandlers
): () => void {
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
    client.subscribe(`/topic/schedules/${scheduleId}/seats`, (message) => {
      try {
        const event = JSON.parse(message.body) as SeatStatusEvent
        if (event.scheduleId === scheduleId) {
          handlers.onEvent(event)
        }
      } catch {
        // Ignore malformed realtime payloads and keep the HTTP flow available.
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
