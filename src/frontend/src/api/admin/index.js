import { api } from 'src/boot/axios'

export const adminApi = {
  getClients() {
    return api.get('/api/admin/clients')
  },
  createClient(data) {
    return api.post('/api/admin/clients', data)
  },
  getApiKeys(clientId) {
    return api.get(`/api/admin/clients/${clientId}/keys`)
  },
  createApiKey(clientId, label) {
    return api.post(
      `/api/admin/clients/${clientId}/keys`,
      label ? { label } : undefined
    )
  },
  revokeApiKey(clientId, keyId) {
    return api.delete(`/api/admin/clients/${clientId}/keys/${keyId}`)
  },
  getTopupHistory(params = {}) {
    // params may include: topupStatus, clientId, from, to — all optional
    return api.get('/api/admin/topups/history', { params })
  },
  approveTopup(topupId) {
    // topupId MUST be in "top_XXX" format — raw numeric id will return 404
    return api.put(`/api/admin/topups/${topupId}/approve`)
  },
  rejectTopup(topupId) {
    return api.put(`/api/admin/topups/${topupId}/reject`)
  },

  // SMSM-01: Scheduled SMS list — params: { clientId, page, size } all optional
  getScheduledSms(params = {}) {
    return api.get('/api/admin/sms/monitor/scheduled', { params })
  },

  // SMSM-02: Per-recipient DLR for a sendRequestId — params: { page, size } optional
  getDlrForRequest(sendRequestId, params = {}) {
    return api.get(`/api/admin/sms/monitor/scheduled/${sendRequestId}/dlr`, { params })
  },

  // WEBH-01: All webhook registrations — params: { page, size } optional
  getWebhookEndpoints(params = {}) {
    return api.get('/api/admin/webhooks/endpoints', { params })
  },

  // WEBH-02: Webhook delivery records — params: { clientId, attemptStatus, page, size } all optional
  getWebhookDeliveries(params = {}) {
    return api.get('/api/admin/webhooks/deliveries', { params })
  },

  // DASH-01: Delivery stats for SMS card
  getDeliveryStats(params = {}) {
    return api.get('/api/admin/sms/analytics/delivery-stats', { params })
  },

  // DASH-01: Credit spend summary for billing card
  getCreditSummary(params = {}) {
    return api.get('/api/admin/credits/summary', { params })
  },

  // DASH-02: Nexah circuit breaker state
  getCircuitBreakerHealth() {
    return api.get('/api/admin/health/nexah/circuit-breaker')
  },

  // DASH-01: Nexah provider send aggregate
  getProviderStats() {
    return api.get('/api/admin/health/nexah/provider-stats')
  },

  // DASH-01: Webhook delivery health aggregate
  getWebhookHealth() {
    return api.get('/api/admin/health/webhooks/stats')
  }
}
