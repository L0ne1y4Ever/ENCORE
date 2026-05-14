import { mockRequest } from './index'
import { mockShows, mockSchedules } from '../mock/shows'
import type { Show, Schedule } from '../mock/shows'

export function getShowList(): Promise<Show[]> {
  return mockRequest(mockShows)
}

export function getShowDetail(id: string): Promise<Show | undefined> {
  const show = mockShows.find(s => s.id === id)
  return mockRequest(show)
}

export function getShowSchedules(showId: string): Promise<Schedule[]> {
  const schedules = mockSchedules[showId] || []
  return mockRequest(schedules)
}
