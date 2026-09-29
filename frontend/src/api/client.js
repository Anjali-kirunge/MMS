const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

const TOKEN_KEY = 'mams.token'
const USER_KEY = 'mams.user'

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }

  get fieldErrors() {
    return (this.payload && this.payload.fieldErrors) || {}
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function storeSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function buildUrl(path, params) {
  const entries = Object.entries(params || {}).filter(
    ([, value]) => value !== undefined && value !== null && value !== '',
  )
  const query = entries.length ? `?${new URLSearchParams(entries)}` : ''
  return `${BASE_URL}${path}${query}`
}

function parseBody(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

export async function request(path, { method = 'GET', body, params } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(buildUrl(path, params), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204) return null

  const payload = parseBody(await response.text())
  if (!response.ok) {
    throw new ApiError(
      (payload && payload.message) || `Request failed with status ${response.status}`,
      response.status,
      payload,
    )
  }
  return payload
}

export const api = {
  // Auth
  login: (username, password) =>
    request('/api/auth/login', { method: 'POST', body: { username, password } }),
  me: () => request('/api/auth/me'),

  // Reference data
  bases: () => request('/api/bases'),
  createBase: (body) => request('/api/bases', { method: 'POST', body }),
  updateBase: (id, body) => request(`/api/bases/${id}`, { method: 'PUT', body }),
  deleteBase: (id) => request(`/api/bases/${id}`, { method: 'DELETE' }),

  equipmentTypes: () => request('/api/equipment-types'),
  createEquipmentType: (body) => request('/api/equipment-types', { method: 'POST', body }),
  updateEquipmentType: (id, body) => request(`/api/equipment-types/${id}`, { method: 'PUT', body }),
  deleteEquipmentType: (id) => request(`/api/equipment-types/${id}`, { method: 'DELETE' }),

  personnel: (params) => request('/api/personnel', { params }),
  personnelSearch: (params) => request('/api/personnel/search', { params }),
  createPersonnel: (body) => request('/api/personnel', { method: 'POST', body }),
  updatePersonnel: (id, body) => request(`/api/personnel/${id}`, { method: 'PUT', body }),
  deletePersonnel: (id) => request(`/api/personnel/${id}`, { method: 'DELETE' }),

  // Dashboard
  dashboard: (params) => request('/api/dashboard', { params }),
  movements: (params) => request('/api/dashboard/movements', { params }),

  // Inventory
  inventory: (params) => request('/api/inventory', { params }),
  inventoryAll: (params) => request('/api/inventory/all', { params }),

  // Transactions
  purchases: (params) => request('/api/purchases', { params }),
  createPurchase: (body) => request('/api/purchases', { method: 'POST', body }),

  transfers: (params) => request('/api/transfers', { params }),
  createTransfer: (body) => request('/api/transfers', { method: 'POST', body }),

  assignments: (params) => request('/api/assignments', { params }),
  createAssignment: (body) => request('/api/assignments', { method: 'POST', body }),

  expenditures: (params) => request('/api/expenditures', { params }),
  createExpenditure: (body) => request('/api/expenditures', { method: 'POST', body }),

  // Administration
  users: (params) => request('/api/users', { params }),
  createUser: (body) => request('/api/users', { method: 'POST', body }),
  updateUser: (id, body) => request(`/api/users/${id}`, { method: 'PUT', body }),
  setUserEnabled: (id, enabled) => request(`/api/users/${id}/enabled?enabled=${enabled}`, { method: 'PATCH' }),
  resetPassword: (id, newPassword) =>
    request(`/api/users/${id}/password`, { method: 'PATCH', body: { newPassword } }),
  deleteUser: (id) => request(`/api/users/${id}`, { method: 'DELETE' }),

  auditLogs: (params) => request('/api/audit-logs', { params }),
  auditEntities: () => request('/api/audit-logs/entities'),
}

export default api
