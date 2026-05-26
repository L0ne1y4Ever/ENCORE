import { apiClient, requestData } from './index'

export type ScheduleStatus = 'COMING_SOON' | 'PREPARING' | 'ON_SALE' | 'SOLD_OUT' | 'CANCELLED'
export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'REFUNDED'
export type AdminShowStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface AdminDashboardTrendItem {
  date: string
  revenue: number | string
  ticketCount: number
}

export interface AdminDashboardTopShow {
  showId: string
  showTitle: string
  ticketCount: number
  revenue: number | string
}

export interface AdminDashboardCheckInSummary {
  checkedIn: number
  unused: number
  voided: number
}

export interface AdminDashboard {
  totalRevenue: number | string
  ticketsSold: number
  activeShows: number
  avgAttendance: number | string
  salesTrend: AdminDashboardTrendItem[]
  topShows: AdminDashboardTopShow[]
  checkInSummary: AdminDashboardCheckInSummary
}

export interface AdminShow {
  id: string
  title: string
  subtitle: string
  coverUrl: string
  description: string
  intro?: string | null
  castMembers?: string | null
  creativeTeam?: string | null
  fullSynopsis?: string | null
  duration: number
  category: string
  tags: string[]
  status: AdminShowStatus
  sortOrder: number
  scheduleCount: number
}

export interface AdminShowPayload {
  title: string
  subtitle: string
  coverUrl: string
  description: string
  intro?: string | null
  castMembers?: string | null
  creativeTeam?: string | null
  fullSynopsis?: string | null
  duration: number
  category: string
  tags: string[]
  status?: AdminShowStatus
  sortOrder?: number
}

export interface AdminSchedule {
  id: string
  showId: string
  showTitle: string
  theaterName: string
  startTime: string
  endTime: string
  status: ScheduleStatus
  priceRange: string
  ticketMode: 'SEATED' | 'ZONED' | 'MIXED' | string
  totalSeats: number
  availableSeats: number
  lockedSeats: number
  soldSeats: number
  disabledSeats: number
  paidTickets: number
  checkedInTickets: number
}

export interface CreateAdminSchedulePayload {
  showId: string
  theaterName: string
  startTime: string
  endTime: string
  status?: ScheduleStatus
  priceRange: string
  ticketMode: 'SEATED' | 'ZONED' | 'MIXED' | string
  seatRows?: number
  seatCols?: number
  vipPrice?: number
  standardPrice?: number
  economyPrice?: number
}

export interface UpdateAdminSchedulePayload {
  showId: string
  theaterName: string
  startTime: string
  endTime: string
  status: ScheduleStatus
  priceRange: string
  ticketMode?: 'SEATED' | 'ZONED' | 'MIXED' | string
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

export function getAdminDashboard(): Promise<AdminDashboard> {
  return requestData<AdminDashboard>(apiClient.get('/api/admin/dashboard'))
}

export function getAdminShows(): Promise<AdminShow[]> {
  return requestData<AdminShow[]>(apiClient.get('/api/admin/shows'))
}

export function createAdminShow(payload: AdminShowPayload): Promise<AdminShow> {
  return requestData<AdminShow>(apiClient.post('/api/admin/shows', payload))
}

export function updateAdminShow(showId: string, payload: AdminShowPayload): Promise<AdminShow> {
  return requestData<AdminShow>(apiClient.put(`/api/admin/shows/${showId}`, payload))
}

export function updateAdminShowStatus(showId: string, status: AdminShowStatus): Promise<AdminShow> {
  return requestData<AdminShow>(apiClient.patch(`/api/admin/shows/${showId}/status`, { status }))
}

export function archiveAdminShow(showId: string): Promise<AdminShow> {
  return requestData<AdminShow>(apiClient.delete(`/api/admin/shows/${showId}`))
}

export function getAdminSchedules(): Promise<AdminSchedule[]> {
  return requestData<AdminSchedule[]>(apiClient.get('/api/admin/schedules'))
}

export function createAdminSchedule(payload: CreateAdminSchedulePayload): Promise<AdminSchedule> {
  return requestData<AdminSchedule>(apiClient.post('/api/admin/schedules', payload))
}

export function updateAdminSchedule(scheduleId: string, payload: UpdateAdminSchedulePayload): Promise<AdminSchedule> {
  return requestData<AdminSchedule>(apiClient.put(`/api/admin/schedules/${scheduleId}`, payload))
}

export function updateAdminScheduleStatus(scheduleId: string, status: ScheduleStatus): Promise<AdminSchedule> {
  return requestData<AdminSchedule>(
    apiClient.patch(`/api/admin/schedules/${scheduleId}/status`, { status })
  )
}

export function cancelAdminSchedule(scheduleId: string): Promise<AdminSchedule> {
  return requestData<AdminSchedule>(apiClient.delete(`/api/admin/schedules/${scheduleId}`))
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
