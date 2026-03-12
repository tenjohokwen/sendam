const routes = [
  // All pages wrapped in MainLayout
  {
    path: '/',
    component: () => import('layouts/MainLayout.vue'),
    children: [
      // Root redirect to login
      {
        path: '',
        redirect: '/login',
      },

      // Auth pages (guest only)
      {
        path: 'login',
        component: () => import('pages/auth/LoginPage.vue'),
        meta: { requiresGuest: true },
      },
      {
        path: 'register',
        component: () => import('pages/auth/RegisterPage.vue'),
        meta: { requiresGuest: true },
      },
      {
        path: 'otp',
        component: () => import('pages/auth/OtpPage.vue'),
        meta: { requiresGuest: true },
      },
      {
        path: 'forgot-password',
        component: () => import('pages/auth/ForgotPasswordPage.vue'),
        meta: { requiresGuest: true },
      },
      {
        path: 'reset-password',
        component: () => import('pages/auth/ResetPasswordPage.vue'),
        meta: { requiresGuest: true },
      },
      {
        path: 'activate',
        component: () => import('pages/auth/ActivatePage.vue'),
        meta: { requiresGuest: true },
      },

      // Protected pages (auth required)
      {
        path: 'dashboard',
        component: () => import('pages/DashboardPage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('pages/ProfilePage.vue'),
        meta: { requiresAuth: true },
      },

      // Admin section (Phase 14-17 will build real content into these pages)
      {
        path: 'admin',
        meta: { requiresAuth: true, requiresAdmin: true },
        children: [
          { path: '', redirect: '/admin/clients' },
          { path: 'clients', component: () => import('pages/admin/ClientsPage.vue') },
          { path: 'topups', component: () => import('pages/admin/TopupsPage.vue') },
          { path: 'sms', component: () => import('pages/admin/SmsMonitorPage.vue') },
          { path: 'webhooks', component: () => import('pages/admin/WebhooksPage.vue') },
          { path: 'audit', component: () => import('pages/admin/AuditPage.vue') },
          { path: 'dashboard', component: () => import('pages/admin/AdminDashboardPage.vue') },
        ],
      },
    ],
  },

  // Catch-all 404
  {
    path: '/:catchAll(.*)*',
    component: () => import('pages/ErrorNotFound.vue'),
  },
]

export default routes
