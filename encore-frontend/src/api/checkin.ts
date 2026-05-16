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

export function checkInTicket(ticketCode: string): Promise<CheckInResponse> {
  return requestData<CheckInResponse>(
    apiClient.post('/api/checkin/verify', { ticketCode })
  )
}
