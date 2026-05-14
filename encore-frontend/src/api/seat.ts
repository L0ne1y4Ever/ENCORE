import { apiClient, requestData } from './index'
import type { Seat } from '../mock/seats'

export function getSeatMap(scheduleId: string): Promise<Seat[]> {
  return requestData<Seat[]>(apiClient.get(`/api/schedules/${scheduleId}/seats`))
}

export function lockSeats(scheduleId: string, seatIds: string[]): Promise<boolean> {
  return requestData<boolean>(
    apiClient.post(`/api/schedules/${scheduleId}/seats/lock`, { seatIds })
  )
}
