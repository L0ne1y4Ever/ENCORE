import axios from 'axios'
import { AUTH_EXPIRED_EVENT, apiClient, clearAuthToken, requestData } from './index'

export type ScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'COMING_SOON' | 'PREPARING' | 'ON_SALE' | 'SOLD_OUT' | 'CANCELLED' | 'ENDED'
export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PENDING_REFUND' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED'
export type AdminShowStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type TicketMode = 'SEATED' | 'ZONED' | 'MIXED'
export type PublishStatus = 'DRAFT' | 'PUBLISHED'
export type VenueStatus = 'ACTIVE' | 'INACTIVE'
export type HallType = 'THEATER' | 'CINEMA' | 'STADIUM'
export type LayoutStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type SeatStatus = 'AVAILABLE' | 'LOCKED' | 'SOLD' | 'DISABLED'
export type StaffRole = 'admin' | 'checker' | 'sysadmin'
export type StaffStatus = 'ACTIVE' | 'INACTIVE'

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

export interface AdminBoxOfficeSummary {
  salesRevenue: number | string
  refundAmount: number | string
  netRevenue: number | string
  pendingAmount: number | string
  paidTickets: number
  refundedTickets: number
  validTickets: number
  checkedInTickets: number
  attendanceRate: number | string
}

export interface AdminBoxOfficeTrendItem {
  date: string
  salesRevenue: number | string
  refundAmount: number | string
  netRevenue: number | string
  pendingAmount: number | string
  paidTickets: number
  refundedTickets: number
  validTickets: number
}

export interface AdminBoxOfficeShowRow {
  showId: string
  showTitle: string
  category: string
  salesRevenue: number | string
  refundAmount: number | string
  netRevenue: number | string
  pendingAmount: number | string
  paidTickets: number
  refundedTickets: number
  validTickets: number
  checkedInTickets: number
  attendanceRate: number | string
  scheduleCount: number
}

export interface AdminBoxOfficeCategoryRow {
  category: string
  salesRevenue: number | string
  refundAmount: number | string
  netRevenue: number | string
  pendingAmount: number | string
  paidTickets: number
  refundedTickets: number
  validTickets: number
  checkedInTickets: number
  attendanceRate: number | string
  showCount: number
  scheduleCount: number
}

export interface AdminBoxOfficeScheduleRow {
  scheduleId: string
  showId: string
  showTitle: string
  theaterName: string
  startTime: string | null
  salesRevenue: number | string
  refundAmount: number | string
  netRevenue: number | string
  pendingAmount: number | string
  paidTickets: number
  refundedTickets: number
  validTickets: number
  checkedInTickets: number
  attendanceRate: number | string
}

export interface AdminBoxOffice {
  globalSummary?: AdminBoxOfficeSummary | null
  summary: AdminBoxOfficeSummary
  trends: AdminBoxOfficeTrendItem[]
  categories: AdminBoxOfficeCategoryRow[]
  shows: AdminBoxOfficeShowRow[]
  schedules: AdminBoxOfficeScheduleRow[]
}

export interface AdminBoxOfficeQuery {
  range?: 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'ALL' | 'CUSTOM'
  startDate?: string
  endDate?: string
  showId?: string
  category?: string
}

export interface AdminDashboard {
  totalRevenue: number | string
  ticketsSold: number
  activeShows: number
  avgAttendance: number | string
  salesTrend: AdminDashboardTrendItem[]
  topShows: AdminDashboardTopShow[]
  checkInSummary: AdminDashboardCheckInSummary
  financeSummary?: AdminBoxOfficeSummary | null
}

export interface AdminOperationLog {
  id: string
  actorId?: string | null
  actorUsername?: string | null
  actorRole?: string | null
  module: string
  action: string
  targetId?: string | null
  targetLabel?: string | null
  result: 'SUCCESS' | 'FAILED' | string
  detail?: string | null
  createdAt: string
}

export interface AdminAuditLogQuery {
  module?: string
  result?: string
  keyword?: string
  limit?: number
}

export interface AdminCsvDownload {
  blob: Blob
  filename: string
}

export interface AdminShowCategoryOption {
  category: string
  showCount: number
}

export interface AdminShowFilterOption {
  id: string
  title: string
  subtitle?: string | null
  category: string
  status: AdminShowStatus | string
  scheduleCount: number
}

export interface RefundRequestSummary {
  id: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | string
  source: 'USER_AUTO' | 'USER_REVIEW' | 'ADMIN_DIRECT' | string
  scope?: 'ORDER' | 'TICKET' | string | null
  reason?: string | null
  reviewNote?: string | null
  reviewerUsername?: string | null
  refundAmount?: number | string | null
  ticketCount?: number | null
  ticketIds?: string[] | null
  requesterId?: string | null
  requestedAt?: string | null
  reviewedAt?: string | null
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

export interface AdminVenue {
  id: string
  name: string
  city?: string | null
  address?: string | null
  status: VenueStatus | string
  hallCount: number
}

export interface AdminHall {
  id: string
  venueId: string
  venueName: string
  name: string
  hallType: HallType | string
  capacity: number
  clearanceMinutes: number
  defaultLayoutId?: string | null
  status: VenueStatus | string
  layoutCount: number
}

export interface AdminLayout {
  id: string
  hallId: string
  hallName: string
  name: string
  ticketMode: TicketMode | string
  version: number
  status: LayoutStatus | string
  areaCount: number
  seatCount: number
}

export interface AdminLayoutArea {
  id: string
  layoutId: string
  name: string
  code: string
  areaType: string
  isSeated: boolean
  capacity: number
  basePrice: number | string
  color?: string | null
  description?: string | null
  positionData?: string | null
}

export interface AdminLayoutSeat {
  id: string
  layoutId: string
  areaId?: string | null
  seatCode: string
  rowNo: number
  colNo: number
  section: string
  status: SeatStatus | string
  price: number | string
}

export interface AdminSchedule {
  id: string
  showId: string
  showTitle: string
  category: string
  hallId?: string | null
  hallName?: string | null
  layoutId?: string | null
  layoutName?: string | null
  theaterName: string
  startTime: string
  endTime: string
  saleStartTime?: string | null
  saleEndTime?: string | null
  status: ScheduleStatus
  publishStatus: PublishStatus | string
  priceRange: string
  basePrice?: number | string | null
  vipPrice?: number | string | null
  standardPrice?: number | string | null
  economyPrice?: number | string | null
  ticketMode: TicketMode | string
  totalSeats: number
  availableSeats: number
  lockedSeats: number
  soldSeats: number
  disabledSeats: number
  paidTickets: number
  checkedInTickets: number
}

export interface AdminScheduleInventory {
  scheduleId: string
  showTitle: string
  theaterName: string
  ticketMode: TicketMode | string
  totalSeats: number
  availableSeats: number
  lockedSeats: number
  soldSeats: number
  disabledSeats: number
  seats: AdminScheduleInventorySeat[]
  areas: AdminScheduleInventoryArea[]
}

export interface AdminScheduleInventorySeat {
  id: string
  row: number
  col: number
  section: string
  status: SeatStatus | string
  price: number | string
}

export interface AdminScheduleInventoryArea {
  id: string
  areaId: string
  name: string
  code: string
  areaType: string
  isSeated: boolean
  price: number | string
  totalCount: number
  availableCount: number
  lockedCount: number
  soldCount: number
  status: string
  color?: string | null
  description?: string | null
  positionData?: string | null
}

export interface LayoutSeatStatusSyncResult {
  layoutId: string
  scheduleCount: number
  updatedSeatCount: number
  scheduleIds: string[]
}

export interface AdminStaffUser {
  id: string
  username: string
  role: StaffRole | string
  displayName: string
  status: StaffStatus | string
  createdAt?: string | null
  updatedAt?: string | null
  editable: boolean
}

export interface AdminVenuePayload {
  name: string
  city?: string | null
  address?: string | null
  status?: VenueStatus | string
}

export interface AdminHallPayload {
  venueId: string
  name: string
  hallType: HallType | string
  capacity: number
  clearanceMinutes: number
  defaultLayoutId?: string | null
  status?: VenueStatus | string
}

export interface AdminLayoutPayload {
  hallId: string
  name: string
  ticketMode: TicketMode | string
  status?: LayoutStatus | string
  seatRows?: number
  seatCols?: number
  vipPrice?: number
  standardPrice?: number
  economyPrice?: number
}

export interface UpdateAdminLayoutPayload {
  name: string
  status?: LayoutStatus | string
}

export interface CreateStaffUserPayload {
  username: string
  password: string
  displayName: string
  role: 'admin' | 'checker'
  status?: StaffStatus | string
}

export interface UpdateStaffUserPayload {
  displayName: string
  role: 'admin' | 'checker'
  status: StaffStatus | string
}

export interface AdminSchedulePricingPayload {
  basePrice: number
  vipPrice: number
  standardPrice: number
  economyPrice: number
}

export interface CreateAdminSchedulePayload {
  showId: string
  hallId?: string | null
  layoutId?: string | null
  theaterName: string
  startTime: string
  endTime: string
  saleStartTime?: string | null
  saleEndTime?: string | null
  status?: ScheduleStatus
  publishStatus?: PublishStatus | string
  priceRange?: string
  pricing?: AdminSchedulePricingPayload
  ticketMode: TicketMode | string
  seatRows?: number
  seatCols?: number
  vipPrice?: number
  standardPrice?: number
  economyPrice?: number
}

export interface UpdateAdminSchedulePayload {
  showId: string
  hallId?: string | null
  layoutId?: string | null
  theaterName: string
  startTime: string
  endTime: string
  saleStartTime?: string | null
  saleEndTime?: string | null
  status: ScheduleStatus
  publishStatus?: PublishStatus | string
  priceRange?: string
  pricing?: AdminSchedulePricingPayload
  ticketMode?: TicketMode | string
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
  orderChannel?: 'ONLINE' | 'OFFLINE' | string | null
  paymentMethod?: 'SIMULATED' | 'COUNTER' | string | null
  cashierUserId?: string | null
  cashierUsername?: string | null
  ticketCount: number
  checkedInCount: number
  createdAt: string
  paidAt: string | null
  refundRequest?: RefundRequestSummary | null
}

export interface AdminOfflineSalePayload {
  scheduleId: string
  buyerUsername?: string | null
  buyerDisplayName?: string | null
  seatIds?: string[] | null
  areaInventoryId?: string | null
  quantity?: number | null
}

export interface AdminOfflineSaleTicket {
  id: string
  ticketCode: string
  seatId?: string | null
  areaInventoryId?: string | null
  areaName?: string | null
  areaType?: string | null
  seatLabel?: string | null
  price: number | string
  status: string
  holderUserId: string
  holderDisplayName: string
}

export interface AdminOfflineSaleResponse {
  order: AdminOrder
  totalAmount: number | string
  tickets: AdminOfflineSaleTicket[]
}

export interface ReviewRefundPayload {
  note?: string | null
}

export function getAdminDashboard(): Promise<AdminDashboard> {
  return requestData<AdminDashboard>(apiClient.get('/api/admin/dashboard'))
}

export function getAdminBoxOffice(params: AdminBoxOfficeQuery = {}): Promise<AdminBoxOffice> {
  return requestData<AdminBoxOffice>(apiClient.get('/api/admin/box-office', { params }))
}

function filenameFromContentDisposition(header: unknown, fallback: string) {
  if (typeof header !== 'string') return fallback
  const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1].trim())
  const plainMatch = header.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1]?.trim() || fallback
}

async function downloadAdminCsv(path: string, fallbackFilename: string): Promise<AdminCsvDownload> {
  try {
    const response = await apiClient.get<Blob>(path, { responseType: 'blob' })
    const contentType = String(response.headers['content-type'] || '')
    if (contentType.includes('application/json')) {
      throw new Error(await messageFromBlob(response.data, 'CSV export failed'))
    }
    return {
      blob: response.data,
      filename: filenameFromContentDisposition(response.headers['content-disposition'], fallbackFilename)
    }
  } catch (error) {
    if (axios.isAxiosError<Blob>(error)) {
      if (error.response?.status === 401 || error.response?.status === 403) {
        clearAuthToken()
        window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
      }
      if (error.response?.data instanceof Blob) {
        throw new Error(await messageFromBlob(error.response.data, error.message))
      }
    }
    throw error
  }
}

async function messageFromBlob(blob: Blob, fallback: string) {
  const text = await blob.text()
  try {
    const body = JSON.parse(text) as { msg?: string }
    return body.msg || fallback
  } catch {
    return text || fallback
  }
}

export function downloadAdminDashboardCsv(): Promise<AdminCsvDownload> {
  return downloadAdminCsv('/api/admin/dashboard/export', `encore-dashboard-${new Date().toISOString().slice(0, 10)}.csv`)
}

export function downloadAdminOrdersCsv(): Promise<AdminCsvDownload> {
  return downloadAdminCsv('/api/admin/orders/export', `encore-orders-${new Date().toISOString().slice(0, 10)}.csv`)
}

export function downloadAdminBoxOfficeCsv(params: AdminBoxOfficeQuery = {}): Promise<AdminCsvDownload> {
  return downloadAdminCsv(
    `/api/admin/box-office/export${queryString(params)}`,
    `encore-box-office-${new Date().toISOString().slice(0, 10)}.csv`
  )
}

function queryString(params: AdminBoxOfficeQuery) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function getAdminAuditLogs(params: AdminAuditLogQuery = {}): Promise<AdminOperationLog[]> {
  return requestData<AdminOperationLog[]>(apiClient.get('/api/admin/audit-logs', { params }))
}

export function getAdminShows(): Promise<AdminShow[]> {
  return requestData<AdminShow[]>(apiClient.get('/api/admin/shows'))
}

export function getAdminShowCategories(): Promise<AdminShowCategoryOption[]> {
  return requestData<AdminShowCategoryOption[]>(apiClient.get('/api/admin/show-categories'))
}

export function getAdminShowOptions(params: {
  category?: string
  keyword?: string
  limit?: number
} = {}): Promise<AdminShowFilterOption[]> {
  return requestData<AdminShowFilterOption[]>(apiClient.get('/api/admin/show-options', { params }))
}

export function getAdminVenues(): Promise<AdminVenue[]> {
  return requestData<AdminVenue[]>(apiClient.get('/api/admin/venues'))
}

export function createAdminVenue(payload: AdminVenuePayload): Promise<AdminVenue> {
  return requestData<AdminVenue>(apiClient.post('/api/admin/venues', payload))
}

export function updateAdminVenue(venueId: string, payload: AdminVenuePayload): Promise<AdminVenue> {
  return requestData<AdminVenue>(apiClient.put(`/api/admin/venues/${venueId}`, payload))
}

export function deleteAdminVenue(venueId: string): Promise<void> {
  return requestData<void>(apiClient.delete(`/api/admin/venues/${venueId}`))
}

export function getAdminHalls(venueId?: string): Promise<AdminHall[]> {
  return requestData<AdminHall[]>(apiClient.get('/api/admin/halls', { params: { venueId } }))
}

export function createAdminHall(payload: AdminHallPayload): Promise<AdminHall> {
  return requestData<AdminHall>(apiClient.post('/api/admin/halls', payload))
}

export function updateAdminHall(hallId: string, payload: AdminHallPayload): Promise<AdminHall> {
  return requestData<AdminHall>(apiClient.put(`/api/admin/halls/${hallId}`, payload))
}

export function deleteAdminHall(hallId: string): Promise<void> {
  return requestData<void>(apiClient.delete(`/api/admin/halls/${hallId}`))
}

export function getAdminLayouts(hallId?: string): Promise<AdminLayout[]> {
  return requestData<AdminLayout[]>(apiClient.get('/api/admin/layouts', { params: { hallId } }))
}

export function createAdminLayout(payload: AdminLayoutPayload): Promise<AdminLayout> {
  return requestData<AdminLayout>(apiClient.post('/api/admin/layouts', payload))
}

export function updateAdminLayout(layoutId: string, payload: UpdateAdminLayoutPayload): Promise<AdminLayout> {
  return requestData<AdminLayout>(apiClient.put(`/api/admin/layouts/${layoutId}`, payload))
}

export function updateAdminLayoutStatus(layoutId: string, status: LayoutStatus): Promise<AdminLayout> {
  return requestData<AdminLayout>(apiClient.patch(`/api/admin/layouts/${layoutId}/status`, { status }))
}

export function getAdminLayoutAreas(layoutId: string): Promise<AdminLayoutArea[]> {
  return requestData<AdminLayoutArea[]>(apiClient.get(`/api/admin/layouts/${layoutId}/areas`))
}

export function getAdminLayoutSeats(layoutId: string): Promise<AdminLayoutSeat[]> {
  return requestData<AdminLayoutSeat[]>(apiClient.get(`/api/admin/layouts/${layoutId}/seats`))
}

export function updateAdminLayoutSeatStatus(
  layoutId: string,
  seatCode: string,
  status: SeatStatus | string
): Promise<AdminLayoutSeat> {
  return requestData<AdminLayoutSeat>(
    apiClient.patch(`/api/admin/layouts/${layoutId}/seats/${seatCode}/status`, { status })
  )
}

export function syncAdminLayoutSeatStatus(layoutId: string, scheduleIds: string[]): Promise<LayoutSeatStatusSyncResult> {
  return requestData<LayoutSeatStatusSyncResult>(
    apiClient.post(`/api/admin/layouts/${layoutId}/sync-seat-status`, { scheduleIds })
  )
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

export function getAdminScheduleInventory(scheduleId: string): Promise<AdminScheduleInventory> {
  return requestData<AdminScheduleInventory>(apiClient.get(`/api/admin/schedules/${scheduleId}/inventory`))
}

export function updateAdminScheduleSeatStatus(
  scheduleId: string,
  seatCode: string,
  status: SeatStatus | string
): Promise<AdminScheduleInventory> {
  return requestData<AdminScheduleInventory>(
    apiClient.patch(`/api/admin/schedules/${scheduleId}/inventory/seats/${seatCode}`, { status })
  )
}

export function updateAdminScheduleAreaInventory(
  scheduleId: string,
  inventoryId: string,
  totalCount: number,
  availableCount: number,
  status: string
): Promise<AdminScheduleInventory> {
  return requestData<AdminScheduleInventory>(
    apiClient.patch(`/api/admin/schedules/${scheduleId}/inventory/areas/${inventoryId}`, {
      totalCount,
      availableCount,
      status
    })
  )
}

export function getAdminOrders(): Promise<AdminOrder[]> {
  return requestData<AdminOrder[]>(apiClient.get('/api/admin/orders'))
}

export function createAdminOfflineSale(payload: AdminOfflineSalePayload): Promise<AdminOfflineSaleResponse> {
  return requestData<AdminOfflineSaleResponse>(apiClient.post('/api/admin/offline-sales', payload))
}

export function refundAdminOrder(orderId: string): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/refund`))
}

export function approveAdminRefund(orderId: string, payload: ReviewRefundPayload = {}): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/refund/approve`, payload))
}

export function rejectAdminRefund(orderId: string, payload: ReviewRefundPayload = {}): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/refund/reject`, payload))
}

export function forceCheckInAdminOrder(orderId: string): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/force-checkin`))
}

export function getAdminStaffUsers(): Promise<AdminStaffUser[]> {
  return requestData<AdminStaffUser[]>(apiClient.get('/api/admin/users/staff'))
}

export function createAdminStaffUser(payload: CreateStaffUserPayload): Promise<AdminStaffUser> {
  return requestData<AdminStaffUser>(apiClient.post('/api/admin/users/staff', payload))
}

export function updateAdminStaffUser(userId: string, payload: UpdateStaffUserPayload): Promise<AdminStaffUser> {
  return requestData<AdminStaffUser>(apiClient.patch(`/api/admin/users/staff/${userId}`, payload))
}

export function resetAdminStaffPassword(userId: string, password: string): Promise<AdminStaffUser> {
  return requestData<AdminStaffUser>(
    apiClient.post(`/api/admin/users/staff/${userId}/reset-password`, { password })
  )
}
