import { apiClient, requestData } from './index'
import type { Order } from '../mock/orders'

export function createOrder(userId: string, scheduleId: string, seatIds: string[], totalAmount: number): Promise<string> {
  return requestData<string>(
    apiClient.post('/api/orders', { userId, scheduleId, seatIds, totalAmount })
  )
}

export function getOrderDetail(orderId: string): Promise<Order> {
  return requestData<Order>(apiClient.get(`/api/orders/${orderId}`))
}

export function simulatePayment(orderId: string): Promise<boolean> {
  return requestData<boolean>(apiClient.post(`/api/orders/${orderId}/pay`))
}
