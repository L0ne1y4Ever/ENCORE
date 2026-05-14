import { defineStore } from 'pinia'
import { ref } from 'vue'
import { mockUsers } from '../mock/users'
import type { User } from '../mock/users'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<User | null>(null)
  
  function login(username: string, password?: string) {
    const user = mockUsers.find(u => u.username === username && u.password === password)
    if (user) {
      currentUser.value = user
      return true
    }
    return false
  }

  function logout() {
    currentUser.value = null
  }

  return {
    currentUser,
    login,
    logout
  }
})
