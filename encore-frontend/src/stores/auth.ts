import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCurrentUserApi, loginApi, logoutApi, registerApi, updateCurrentUserApi } from '../api/auth'
import { AUTH_EXPIRED_EVENT } from '../api'
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
  const sessionChecked = ref(!currentUser.value)

  function clearStoredSession() {
    currentUser.value = null
    saveStoredUser(null)
    sessionChecked.value = true
  }

  if (typeof window !== 'undefined') {
    window.addEventListener(AUTH_EXPIRED_EVENT, clearStoredSession)
  }
  
  async function login(username: string, password: string) {
    const user = await loginApi(username, password)
    currentUser.value = user
    saveStoredUser(user)
    sessionChecked.value = true
    return true
  }

  async function register(username: string, password: string, displayName: string) {
    const user = await registerApi(username, password, displayName)
    currentUser.value = user
    saveStoredUser(user)
    sessionChecked.value = true
    return true
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      clearStoredSession()
    }
  }

  async function refreshCurrentUser() {
    try {
      const user = await getCurrentUserApi()
      currentUser.value = user
      saveStoredUser(user)
      sessionChecked.value = true
      return user
    } catch {
      clearStoredSession()
      return null
    }
  }

  async function ensureSession() {
    if (!currentUser.value) {
      sessionChecked.value = true
      return null
    }
    if (sessionChecked.value) {
      return currentUser.value
    }
    return refreshCurrentUser()
  }

  async function updateNickname(newNickname: string) {
    const user = await updateCurrentUserApi(newNickname)
    const normalizedUser = {
      ...user,
      nickname: user.displayName
    }
    currentUser.value = normalizedUser
    saveStoredUser(normalizedUser)
    return normalizedUser
  }

  return {
    currentUser,
    login,
    register,
    logout,
    refreshCurrentUser,
    ensureSession,
    updateNickname
  }
})
