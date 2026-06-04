import { apiClient, requestData } from './index'

export type ScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'COMING_SOON' | 'PREPARING' | 'ON_SALE' | 'SOLD_OUT' | 'CANCELLED' | 'ENDED'
export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED'
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
  priceRange: string
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
  priceRange: string
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

export function getAdminVenues(): Promise<AdminVenue[]> {
  return requestData<AdminVenue[]>(apiClient.get('/api/admin/venues'))
}

export function createAdminVenue(payload: AdminVenuePayload): Promise<AdminVenue> {
  return requestData<AdminVenue>(apiClient.post('/api/admin/venues', payload))
}

export function updateAdminVenue(venueId: string, payload: AdminVenuePayload): Promise<AdminVenue> {
  return requestData<AdminVenue>(apiClient.put(`/api/admin/venues/${venueId}`, payload))
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

export function refundAdminOrder(orderId: string): Promise<AdminOrder> {
  return requestData<AdminOrder>(apiClient.post(`/api/admin/orders/${orderId}/refund`))
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
