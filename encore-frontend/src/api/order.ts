import { apiClient, requestData } from './index'
import type { Order } from '../mock/orders'

export function createOrder(
  userId: string,
  scheduleId: string,
  seatIds: string[] | null,
  totalAmount: number,
  areaInventoryId?: string | null,
  quantity?: number | null
): Promise<string> {
  return requestData<string>(
    apiClient.post('/api/orders', {
      userId,
      scheduleId,
      seatIds,
      totalAmount,
      areaInventoryId,
      quantity
    })
  )
}

export function getOrderDetail(orderId: string): Promise<Order> {
  return requestData<Order>(apiClient.get(`/api/orders/${orderId}`))
}

export function simulatePayment(orderId: string): Promise<boolean> {
  return requestData<boolean>(apiClient.post(`/api/orders/${orderId}/pay`))
}
