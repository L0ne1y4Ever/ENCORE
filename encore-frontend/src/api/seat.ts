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
  const url = areaId ? `/api/schedules/${scheduleId}/seats?areaId=${areaId}` : `/api/schedules/${scheduleId}/seats`
  return requestData<Seat[]>(apiClient.get(url))
}

export function lockSeats(scheduleId: string, seatIds: string[]): Promise<boolean> {
  return requestData<boolean>(
    apiClient.post(`/api/schedules/${scheduleId}/seats/lock`, { seatIds })
  )
}

export function getScheduleAreas(scheduleId: string): Promise<ScheduleAreaResponse[]> {
  return requestData<ScheduleAreaResponse[]>(apiClient.get(`/api/schedules/${scheduleId}/areas`))
}
