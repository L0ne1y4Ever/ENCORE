import { apiClient, requestData } from './index'
import type { Order } from '../mock/orders'

export function createOrder(
  scheduleId: string,
  seatIds: string[] | null,
  areaInventoryId?: string | null,
  quantity?: number | null
): Promise<string> {
  return requestData<string>(
    apiClient.post('/api/orders', {
      scheduleId,
      seatIds,
      areaInventoryId,
      quantity
    })
  )
}

export function getOrderDetail(orderId: string): Promise<Order> {
  return requestData<Order>(apiClient.get(`/api/orders/${orderId}`))
}

export function getMyOrders(): Promise<Order[]> {
  return requestData<Order[]>(apiClient.get('/api/orders/my'))
}

export function simulatePayment(orderId: string): Promise<boolean> {
  return requestData<boolean>(apiClient.post(`/api/orders/${orderId}/pay`))
}

export function cancelOrder(orderId: string): Promise<Order> {
  return requestData<Order>(apiClient.post(`/api/orders/${orderId}/cancel`))
}

export function refundOrder(orderId: string, reason?: string, ticketIds?: string[]): Promise<Order> {
  const payload = reason?.trim() ? { reason: reason.trim() } : {}
  const body = ticketIds?.length ? { ...payload, ticketIds } : payload
  return requestData<Order>(apiClient.post(`/api/orders/${orderId}/refund`, body))
}
