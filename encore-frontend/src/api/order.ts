import { mockRequest } from './index'
import { mockOrders } from '../mock/orders'
import type { Order } from '../mock/orders'

export function createOrder(userId: string, scheduleId: string, seatIds: string[], totalAmount: number): Promise<string> {
  const newOrderId = `ord-${Date.now()}`
  
  const newOrder: Order = {
    id: newOrderId,
    userId,
    scheduleId,
    totalAmount,
    status: 'PENDING_PAYMENT',
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 15 * 60000).toISOString(),
    tickets: seatIds.map((seatId, idx) => ({
      id: `tk-${Date.now()}-${idx}`,
      orderId: newOrderId,
      scheduleId,
      seatId,
      ticketCode: `${Date.now()}${idx}XYZ`,
      status: 'UNUSED'
    }))
  }
  
  mockOrders.push(newOrder)
  return mockRequest(newOrderId)
}

export function getOrderDetail(orderId: string): Promise<Order | undefined> {
  const order = mockOrders.find(o => o.id === orderId)
  return mockRequest(order)
}

export function simulatePayment(orderId: string): Promise<boolean> {
  const order = mockOrders.find(o => o.id === orderId)
  if (order && order.status === 'PENDING_PAYMENT') {
    order.status = 'PAID'
    return mockRequest(true, 800) // 模拟支付转圈稍长一点
  }
  return mockRequest(false)
}
