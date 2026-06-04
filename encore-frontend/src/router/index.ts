import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue')
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
      { path: 'layouts', name: 'AdminLayouts', component: () => import('../views/admin/Layouts.vue') },
      { path: 'schedules', name: 'AdminSchedules', component: () => import('../views/admin/Schedules.vue') },
      { path: 'schedules/:id/inventory', name: 'AdminScheduleInventory', component: () => import('../views/admin/ScheduleInventory.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('../views/admin/Orders.vue') },
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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const requiredRole = [...to.matched].reverse().find(record => record.meta.role)?.meta.role

  if (requiresAuth && !authStore.currentUser) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (requiresAuth && authStore.currentUser) {
    const userRole = authStore.currentUser.role
    if (requiredRole && requiredRole !== userRole && userRole !== 'sysadmin') {
      // 越权访问，回到自己所属域的首页
      if (userRole === 'admin') next('/admin')
      else if (userRole === 'checker') next('/checkin')
      else next('/')
    } else {
      next()
    }
  } else {
    // 比如访问 /login
    if (to.path === '/login' && authStore.currentUser) {
      next('/') // 已登录不应再进 login
    } else {
      next()
    }
  }
})

export default router
