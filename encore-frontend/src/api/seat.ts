import { mockRequest, mockReject } from './index'
import { mockScheduleSeats } from '../mock/seats'
import type { Seat } from '../mock/seats'

export function getSeatMap(scheduleId: string): Promise<Seat[]> {
  const seats = mockScheduleSeats[scheduleId] || []
  return mockRequest(seats)
}

export function lockSeats(scheduleId: string, seatIds: string[]): Promise<boolean> {
  // 模拟乐观锁或并发冲突，这里简单假设 5% 的概率锁定失败
  const isConflict = Math.random() < 0.05
  if (isConflict) {
    return mockReject(new Error('Seats are no longer available'))
  }
  
  // 更新本地 mock 状态
  const seats = mockScheduleSeats[scheduleId]
  if (seats) {
    seats.forEach(s => {
      if (seatIds.includes(s.id)) {
        s.status = 'LOCKED'
      }
    })
  }
  
  return mockRequest(true)
}
