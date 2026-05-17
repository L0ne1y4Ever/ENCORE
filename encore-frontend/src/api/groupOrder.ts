import { apiClient, requestData } from './index'

export interface GroupOrderMember {
  userId: string
  displayName: string
  seatIds: string[]
  amount: number
  joinedAt: string
}

export interface GroupOrder {
  inviteCode: string
  scheduleId: string
  hostUserId: string
  hostDisplayName: string
  status: 'OPEN' | 'CHECKED_OUT' | 'CANCELLED' | 'EXPIRED'
  expiresAt: string
  maxSeats: number
  totalAmount: number
  members: GroupOrderMember[]
}

export function createGroupOrder(scheduleId: string, seatIds: string[]): Promise<GroupOrder> {
  return requestData<GroupOrder>(apiClient.post('/api/group-orders', { scheduleId, seatIds }))
}

export function getGroupOrder(inviteCode: string): Promise<GroupOrder> {
  return requestData<GroupOrder>(apiClient.get(`/api/group-orders/${inviteCode}`))
}

export function joinGroupOrder(inviteCode: string, seatIds: string[]): Promise<GroupOrder> {
  return requestData<GroupOrder>(apiClient.post(`/api/group-orders/${inviteCode}/join`, { seatIds }))
}

export function leaveGroupOrder(inviteCode: string): Promise<GroupOrder> {
  return requestData<GroupOrder>(apiClient.delete(`/api/group-orders/${inviteCode}/members/me`))
}

export function cancelGroupOrder(inviteCode: string): Promise<GroupOrder> {
  return requestData<GroupOrder>(apiClient.delete(`/api/group-orders/${inviteCode}`))
}

export function checkoutGroupOrder(inviteCode: string): Promise<string> {
  return requestData<string>(apiClient.post(`/api/group-orders/${inviteCode}/checkout`))
}
