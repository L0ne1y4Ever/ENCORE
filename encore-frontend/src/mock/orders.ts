export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'CANCELLED'

export interface TicketItem {
  id: string
  orderId: string
  scheduleId: string
  seatId: string
  ticketCode: string
  status: 'UNUSED' | 'CHECKED_IN' | 'VOID'
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
