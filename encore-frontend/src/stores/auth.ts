import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCurrentUserApi, loginApi, logoutApi, registerApi } from '../api/auth'
import type { UserProfile } from '../api/auth'

const USER_KEY = 'encore.currentUser'

function readStoredUser(): UserProfile | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserProfile
  } catch {
    sessionStorage.removeItem(USER_KEY)
    return null
  }
}

function saveStoredUser(user: UserProfile | null) {
  if (user) {
    sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    sessionStorage.removeItem(USER_KEY)
  }
}

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<UserProfile | null>(readStoredUser())
  
  async function login(username: string, password: string) {
    const user = await loginApi(username, password)
    currentUser.value = user
    saveStoredUser(user)
    return true
  }

  async function register(username: string, password: string, displayName: string) {
    const user = await registerApi(username, password, displayName)
    currentUser.value = user
    saveStoredUser(user)
    return true
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      currentUser.value = null
      saveStoredUser(null)
    }
  }

  async function refreshCurrentUser() {
    try {
      const user = await getCurrentUserApi()
      currentUser.value = user
      saveStoredUser(user)
      return user
    } catch {
      currentUser.value = null
      saveStoredUser(null)
      return null
    }
  }

  function updateNickname(newNickname: string) {
    if (currentUser.value) {
      currentUser.value.nickname = newNickname
      saveStoredUser(currentUser.value)
    }
  }

  return {
    currentUser,
    login,
    register,
    logout,
    refreshCurrentUser,
    updateNickname
  }
})
