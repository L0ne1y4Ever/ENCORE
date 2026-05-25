export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'CANCELLED'

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
  totalAmount: number
  status: OrderStatus
  createdAt: string
  expiresAt: string
  tickets?: TicketItem[]
}

export const mockOrders: Order[] = []
