export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED'

export interface TicketItem {
  id: string
  orderId: string
  scheduleId: string
  seatId: string | null
  ticketCode: string
  status: 'UNUSED' | 'CHECKED_IN' | 'VOID'
  areaInventoryId?: string | null
  areaName?: string | null
  areaType?: string | null
  seatLabel?: string | null
}

export interface Order {
  id: string
  userId: string
  scheduleId: string
  showTitle?: string | null
  theaterName?: string | null
  startTime?: string | null
  totalAmount: number | string
  status: OrderStatus
  createdAt: string
  expiresAt: string
  tickets?: TicketItem[]
}

export const mockOrders: Order[] = []
