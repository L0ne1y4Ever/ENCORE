export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PENDING_REFUND' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED'

export interface TicketItem {
  id: string
  orderId: string
  scheduleId: string
  seatId: string | null
  ticketCode: string
  status: 'RESERVED' | 'UNUSED' | 'PENDING_REFUND' | 'CHECKED_IN' | 'VOID'
  areaInventoryId?: string | null
  areaName?: string | null
  areaType?: string | null
  seatLabel?: string | null
  rowNo?: number | null
  colNo?: number | null
  price?: number | string | null
  holderUserId?: string | null
  holderDisplayName?: string | null
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

export interface Order {
  id: string
  userId: string
  scheduleId: string
  showTitle?: string | null
  theaterName?: string | null
  startTime?: string | null
  endTime?: string | null
  totalAmount: number | string
  status: OrderStatus
  createdAt: string
  expiresAt: string
  refundRequest?: RefundRequestSummary | null
  tickets?: TicketItem[]
}

export const mockOrders: Order[] = []
