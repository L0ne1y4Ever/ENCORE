export interface User {
  id: string
  username: string
  password?: string
  nickname?: string
  role: 'user' | 'admin' | 'checker' | 'sysadmin'
}

export const mockUsers: User[] = [
  { id: 'u-101', username: 'user', password: '123', nickname: 'John Doe', role: 'user' },
  { id: 'u-901', username: 'admin', password: '123', nickname: 'Admin', role: 'admin' },
  { id: 'u-801', username: 'checker', password: '123', nickname: 'Scanner01', role: 'checker' },
  { id: 'u-701', username: 'sysadmin', password: '123', nickname: 'Root', role: 'sysadmin' },
]
