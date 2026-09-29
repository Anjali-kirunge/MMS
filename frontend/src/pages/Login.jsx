import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { ErrorAlert } from '../components/Feedback.jsx'

const TEST_ACCOUNTS = [
  { username: 'admin', password: 'admin123', role: 'ADMIN' },
  { username: 'gen.alpha', password: 'commander123', role: 'BASE_COMMANDER · BAM' },
  { username: 'logistics', password: 'logistics123', role: 'LOGISTICS_OFFICER' },
]

export default function Login() {
  const { login, token } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  // Already signed in (for example after a page reload on /login).
  if (token) {
    return <Navigate to={location.state?.from || '/'} replace />
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate(location.state?.from || '/', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mms-login">
      <div className="mms-login-card">
        <div className="text-center mb-4">
          <img src="/logo.svg" alt="Military Asset Management System" className="mms-login-logo mb-3" width="72" height="72" />
          <div className="text-uppercase text-muted small fw-semibold" style={{ letterSpacing: '0.18em' }}>
            Military
          </div>
          <h1 className="h4 fw-bold mt-1 mb-1">Asset Management System</h1>
          <p className="text-muted mms-hint mb-0">Secure sign-in required</p>
        </div>

        {error ? <ErrorAlert error={error} /> : null}

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label className="form-label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className="form-control"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="mb-4">
            <label className="form-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="form-control"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <hr className="my-4" />
        <p className="mms-hint mb-2 fw-semibold">Test accounts</p>
        <div className="d-flex flex-column gap-1">
          {TEST_ACCOUNTS.map((account) => (
            <button
              key={account.username}
              type="button"
              className="btn btn-sm btn-outline-secondary text-start d-flex justify-content-between"
              onClick={() => {
                setUsername(account.username)
                setPassword(account.password)
                setError(null)
              }}
            >
              <span>
                {account.username} / {account.password}
              </span>
              <span className="mms-hint">{account.role}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
