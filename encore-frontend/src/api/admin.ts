import { apiClient, requestData } from './index'

export type ScheduleStatus = 'COMING_SOON' | 'PREPARING' | 'ON_SALE' | 'SOLD_OUT' | 'CANCELLED'
export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'REFUNDED'

export interface AdminSchedule {
  id: string
  showId: string
  showTitle: string
  theaterName: string
  startTime: string
  endTime: string
  status: ScheduleStatus
  priceRange: string
  totalSeats: number
  availableSeats: number
  lockedSeats: number
  soldSeats: number
  disabledSeats: number
  paidTickets: number
  checkedInTickets: number
}

export interface AdminOrder {
  id: string
  userId: string
  username: string
  scheduleId: string
  showName: string
  theaterName: string
  startTime: string | null
  totalAmount: number | string
  status: AdminOrderStatus
  ticketCount: number
  checkedInCount: number
  createdAt: string
  paidAt: string | null
}

export function getAdminSchedules(): Promise<AdminSchedule[]> {
  return requestData<AdminSchedule[]>(apiClient.get('/api/admin/schedules'))
}

export function updateAdminScheduleStatus(scheduleId: string, status: ScheduleStatus): Promise<AdminSchedule> {
  return requestData<AdminSchedule>(
    apiClient.patch(`/api/admin/schedules/${scheduleId}/status`, { status })
  )
}

export function getAdminOrders(): Promise<AdminOrder[]> {
  return requestData<AdminOrder[]>(apiClient.get('/api/admin/orders'))
}

export function refundAdminOrder(orderId: string): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/refund`))
}

export function forceCheckInAdminOrder(orderId: string): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/force-checkin`))
}
