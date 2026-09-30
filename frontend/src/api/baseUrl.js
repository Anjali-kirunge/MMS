/**
 * The API origin is normalised here and nowhere else, because every path passed
 * to request() already starts with `/api`. A trailing `/api` on the configured
 * origin would otherwise produce requests to `/api/api/...` and break sign-in,
 * which is easy to reintroduce by accident when setting VITE_API_BASE_URL.
 */
const rawBase = (import.meta.env.VITE_API_BASE_URL || '').trim()
export const BASE_URL = rawBase.replace(/\/+$/, '').replace(/\/api$/, '')

export function buildApiUrl(path, params) {
  const entries = Object.entries(params || {}).filter(
    ([, value]) => value !== undefined && value !== null && value !== '',
  )
  const query = entries.length ? `?${new URLSearchParams(entries)}` : ''
  return `${BASE_URL}${path}${query}`
}
