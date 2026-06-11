import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { UserRole } from '../api/auth'

function homeForRole(role?: UserRole | string) {
  if (role === 'admin' || role === 'sysadmin') return '/admin'
  if (role === 'checker') return '/checkin'
  return '/'
}

function canAccessRole(requiredRole: unknown, userRole?: UserRole | string) {
  if (!requiredRole) return true
  if (requiredRole === 'admin') return userRole === 'admin' || userRole === 'sysadmin'
  if (requiredRole === 'sysadmin') return userRole === 'sysadmin'
  if (requiredRole === 'checker') return userRole === 'checker' || userRole === 'admin' || userRole === 'sysadmin'
  return requiredRole === userRole
}

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue')
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/system/Forbidden.vue')
  },
  {
    path: '/',
    component: () => import('../layouts/UserLayout.vue'),
    meta: { requiresAuth: true, role: 'user' },
    children: [
      { path: '', name: 'Home', component: () => import('../views/user/Home.vue') },
      { path: 'show/:id', name: 'ShowDetail', component: () => import('../views/user/ShowDetail.vue') },
      { path: 'seat/:id', name: 'SeatSelection', component: () => import('../views/user/SeatSelection.vue') },
      { path: 'confirm', name: 'OrderConfirm', component: () => import('../views/user/OrderConfirm.vue') },
      { path: 'payment', name: 'Payment', component: () => import('../views/user/Payment.vue') },
      { path: 'ticket/:id', name: 'ETicket', component: () => import('../views/user/ETicket.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/user/Profile.vue') }
    ]
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'admin' },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'shows', name: 'AdminShows', component: () => import('../views/admin/Shows.vue') },
      { path: 'venues', name: 'AdminVenues', component: () => import('../views/admin/Venues.vue') },
      { path: 'layouts', redirect: '/admin/venues?tab=layouts' },
      { path: 'schedules', name: 'AdminSchedules', component: () => import('../views/admin/Schedules.vue') },
      { path: 'schedules/:id/inventory', name: 'AdminScheduleInventory', component: () => import('../views/admin/ScheduleInventory.vue') },
      { path: 'offline-sales', name: 'AdminOfflineSales', component: () => import('../views/admin/OfflineSales.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('../views/admin/Orders.vue') },
      { path: 'finance', name: 'AdminFinance', component: () => import('../views/admin/Finance.vue') },
      { path: 'audit-logs', name: 'AdminAuditLogs', component: () => import('../views/admin/AuditLogs.vue'), meta: { role: 'sysadmin' } },
      { path: 'users', name: 'AdminStaffUsers', component: () => import('../views/admin/StaffUsers.vue'), meta: { role: 'sysadmin' } },
    ]
  },
  {
    path: '/checkin',
    component: () => import('../layouts/CheckinLayout.vue'),
    meta: { requiresAuth: true, role: 'checker' },
    children: [
      { path: '', name: 'Scanner', component: () => import('../views/checkin/Scanner.vue') }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/system/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const requiredRole = [...to.matched].reverse().find(record => record.meta.role)?.meta.role
  if ((requiresAuth || to.path === '/login') && authStore.currentUser) {
    await authStore.ensureSession()
  }

  if (requiresAuth && !authStore.currentUser) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (requiresAuth && authStore.currentUser) {
    const userRole = authStore.currentUser.role
    if (!canAccessRole(requiredRole, userRole)) {
      next({ path: '/403', query: { from: to.fullPath } })
    } else {
      next()
    }
  } else {
    // 比如访问 /login
    if (to.path === '/login' && authStore.currentUser) {
      next(homeForRole(authStore.currentUser.role)) // 已登录不应再进 login
    } else {
      next()
    }
  }
})

export default router
