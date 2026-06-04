import { apiClient, requestData } from './index'

export interface CheckInResponse {
  ticketId: string
  ticketCode: string
  orderId: string
  scheduleId: string
  showTitle: string
  theaterName: string
  startTime: string | null
  seatId: string
  status: 'CHECKED_IN'
  checkedInAt: string
}

export interface CheckInSchedule {
  id: string
  showTitle: string
  category: string
  theaterName: string
  startTime: string | null
  endTime: string | null
  status: string
  checkInOpen: boolean
}

export function getCheckInSchedules(): Promise<CheckInSchedule[]> {
  return requestData<CheckInSchedule[]>(
    apiClient.get('/api/checkin/schedules')
  )
}

export function checkInTicket(ticketCode: string, scheduleId?: string): Promise<CheckInResponse> {
  const payload = scheduleId ? { ticketCode, scheduleId } : { ticketCode }
  return requestData<CheckInResponse>(
    apiClient.post('/api/checkin/verify', payload)
  )
}
