export type SeatStatus = 'AVAILABLE' | 'LOCKED' | 'SOLD' | 'DISABLED'

export interface Seat {
  id: string
  row: number
  col: number
  section: string
  status: SeatStatus
  price: number
}

// 生成一个简易的 10x15 座位矩阵作为 mock
function generateMockSeats(): Seat[] {
  const seats: Seat[] = []
  const rows = 10
  const cols = 15
  
  for (let r = 1; r <= rows; r++) {
    for (let c = 1; c <= cols; c++) {
      // 随机分配一些状态
      const rand = Math.random()
      let status: SeatStatus = 'AVAILABLE'
      if (rand > 0.9) status = 'DISABLED'
      else if (rand > 0.7) status = 'SOLD'
      else if (rand > 0.65) status = 'LOCKED'

      let section = 'VIP'
      let price = 150
      if (r > 3) { section = 'A'; price = 100 }
      if (r > 7) { section = 'B'; price = 50 }

      seats.push({
        id: `seat-${r}-${c}`,
        row: r,
        col: c,
        section,
        status,
        price
      })
    }
  }
  return seats
}

export const mockScheduleSeats: Record<string, Seat[]> = {
  'sch-101': generateMockSeats(),
  'sch-102': generateMockSeats(),
  'sch-201': generateMockSeats(),
}
