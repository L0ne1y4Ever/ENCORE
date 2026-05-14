import { apiClient, requestData } from './index'
import type { Show, Schedule } from '../mock/shows'

export function getShowList(): Promise<Show[]> {
  return requestData<Show[]>(apiClient.get('/api/shows'))
}

export function getShowDetail(id: string): Promise<Show> {
  return requestData<Show>(apiClient.get(`/api/shows/${id}`))
}

export function getShowSchedules(showId: string): Promise<Schedule[]> {
  return requestData<Schedule[]>(apiClient.get(`/api/shows/${showId}/schedules`))
}
