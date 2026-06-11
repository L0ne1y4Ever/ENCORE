import { apiClient, requestData } from './index'
import type { Seat } from '../mock/seats'

export interface ScheduleAreaResponse {
  id: string
  areaId: string
  name: string
  code: string
  areaType: string
  isSeated: boolean
  price: number
  totalCount: number
  availableCount: number
  lockedCount: number
  soldCount: number
  status?: string
  color: string
  description: string
  positionData: string
}

export function getSeatMap(scheduleId: string, areaId?: string): Promise<Seat[]> {
  const params = new URLSearchParams({ _ts: Date.now().toString() })
  if (areaId) {
    params.set('areaId', areaId)
  }
  return requestData<Seat[]>(
    apiClient.get(`/api/schedules/${scheduleId}/seats?${params.toString()}`, {
      headers: { 'Cache-Control': 'no-cache' }
    })
  )
}

export function lockSeats(scheduleId: string, seatIds: string[]): Promise<boolean> {
  return requestData<boolean>(
    apiClient.post(`/api/schedules/${scheduleId}/seats/lock`, { seatIds })
  )
}

export interface SeatLockInfo {
  seatId: string
  expiresAt?: string | null
}

export interface SeatLocksResponse {
  seats: SeatLockInfo[]
}

export function getMySeatLocks(scheduleId: string): Promise<SeatLocksResponse> {
  return requestData<SeatLocksResponse>(
    apiClient.get(`/api/schedules/${scheduleId}/seats/my-locks`)
  )
}

export function unlockSeats(scheduleId: string, seatIds?: string[]): Promise<SeatLocksResponse> {
  return requestData<SeatLocksResponse>(
    apiClient.post(`/api/schedules/${scheduleId}/seats/unlock`, { seatIds: seatIds || [] })
  )
}

export function getScheduleAreas(scheduleId: string): Promise<ScheduleAreaResponse[]> {
  return requestData<ScheduleAreaResponse[]>(
    apiClient.get(`/api/schedules/${scheduleId}/areas?_ts=${Date.now()}`, {
      headers: { 'Cache-Control': 'no-cache' }
    })
  )
}
