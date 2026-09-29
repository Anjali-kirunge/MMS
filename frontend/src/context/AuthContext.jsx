import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, clearSession, getStoredUser, getToken, storeSession } from '../api/client.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser)
  const [initialising, setInitialising] = useState(Boolean(getToken()))

  // Re-validate a stored token against the API on first render so that an
  // expired or revoked token never leaves a half-authenticated UI.
  useEffect(() => {
    let cancelled = false
    if (!getToken()) {
      setInitialising(false)
      return undefined
    }
    api
      .me()
      .then((info) => {
        if (!cancelled) {
          setUser(info)
          storeSession(getToken(), info)
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearSession()
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) setInitialising(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username, password) => {
    const result = await api.login(username, password)
    storeSession(result.token, result.user)
    setUser(result.user)
    return result.user
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({
      user,
      token: getToken(),
      initialising,
      login,
      logout,
      isAdmin: user?.role === 'ADMIN',
      isBaseCommander: user?.role === 'BASE_COMMANDER',
      isLogisticsOfficer: user?.role === 'LOGISTICS_OFFICER',
      /** Frontend convenience only - the backend enforces the real rules. */
      can: (...roles) => Boolean(user) && roles.includes(user.role),
    }),
    [user, initialising, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
