export interface User {
  id: string
  username: string
  password?: string
  role: 'user' | 'admin' | 'checker' | 'sysadmin'
}

export const mockUsers: User[] = [
  { id: 'u-101', username: 'user', password: '123', role: 'user' },
  { id: 'u-901', username: 'admin', password: '123', role: 'admin' },
  { id: 'u-801', username: 'checker', password: '123', role: 'checker' },
  { id: 'u-701', username: 'sysadmin', password: '123', role: 'sysadmin' },
]
