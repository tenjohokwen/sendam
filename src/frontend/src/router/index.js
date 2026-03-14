import { defineRouter } from '#q-app/wrappers'
import {
  createRouter,
  createMemoryHistory,
  createWebHistory,
  createWebHashHistory,
} from 'vue-router'
import routes from './routes'
import { useUserStore } from 'src/stores/user.store'

/*
 * If not building with SSR mode, you can
 * directly export the Router instantiation;
 *
 * The function below can be async too; either use
 * async/await or return a Promise which resolves
 * with the Router instance.
 */

export default defineRouter(function (/* { store, ssrContext } */) {
  const createHistory = process.env.SERVER
    ? createMemoryHistory
    : process.env.VUE_ROUTER_MODE === 'history'
      ? createWebHistory
      : createWebHashHistory

  const Router = createRouter({
    scrollBehavior: () => ({ left: 0, top: 0 }),
    routes,

    // Leave this as is and make changes in quasar.conf.js instead!
    // quasar.conf.js -> build -> vueRouterMode
    // quasar.conf.js -> build -> publicPath
    history: createHistory(process.env.VUE_ROUTER_BASE),
  })

  // Navigation guards for auth protection
  Router.beforeEach(async (to, from, next) => {
    const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)
    const requiresGuest = to.matched.some((r) => r.meta.requiresGuest)
    const requiresAdmin = to.matched.some((r) => r.meta.requiresAdmin)
    const isAuthenticated = document.cookie.includes('user=')

    if (requiresAuth && !isAuthenticated) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    if (requiresAdmin && !isAuthenticated) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    if (requiresAdmin && isAuthenticated) {
      const userStore = useUserStore()
      if (!userStore.isLoaded) {
        try {
          await userStore.fetchUser()
        } catch {
          next({ name: 'login' })
          return
        }
      }
      if (!userStore.isAdmin) {
        next({ name: 'dashboard' })
        return
      }
    }

    if (requiresGuest && isAuthenticated) {
      next('/dashboard')
      return
    }

    next()
  })

  return Router
})
