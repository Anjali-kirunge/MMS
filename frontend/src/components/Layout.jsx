import { useEffect } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', roles: ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER'], end: true },
  { to: '/inventory', label: 'Inventory', roles: ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER'] },
  { to: '/purchases', label: 'Purchases', roles: ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER'] },
  { to: '/transfers', label: 'Transfers', roles: ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER'] },
  { to: '/assignments', label: 'Assignments', roles: ['ADMIN', 'BASE_COMMANDER'] },
  { to: '/expenditures', label: 'Expenditures', roles: ['ADMIN', 'BASE_COMMANDER'] },
  { to: '/audit-logs', label: 'Audit Logs', roles: ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER'] },
  { to: '/personnel', label: 'Personnel', roles: ['ADMIN'] },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
  { to: '/bases', label: 'Bases', roles: ['ADMIN'] },
  { to: '/equipment-types', label: 'Equipment Types', roles: ['ADMIN'] },
]

const ROLE_BADGE = {
  ADMIN: 'bg-danger',
  BASE_COMMANDER: 'bg-primary',
  LOGISTICS_OFFICER: 'bg-success',
}

export default function Layout() {
  const { user, logout, can } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    document.title = 'Military Asset Management System'
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const visibleItems = NAV_ITEMS.filter((item) => can(...item.roles))

  return (
    <div className="d-flex">
      <aside className="mms-sidebar">
        <div className="brand">
          <img src="/logo.svg" alt="" className="brand-logo" width="34" height="34" />
          <span className="brand-text">
            <strong>MAMS</strong>
            <span>Asset Management</span>
          </span>
        </div>
        <nav className="nav flex-column py-2">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="mms-main flex-grow-1">
        <header className="mms-topbar d-flex justify-content-between align-items-center">
          <div className="d-flex align-items-center gap-2">
            <img src="/logo.svg" alt="" width="26" height="26" />
            <h1 className="h6 mb-0 fw-semibold">Military Asset Management System</h1>
          </div>
          <div className="d-flex align-items-center gap-3">
            <div className="text-end">
              <div className="fw-semibold small">{user?.fullName}</div>
              <div className="mms-hint">
                {user?.baseName ? `${user.baseCode} · ${user.baseName}` : 'Headquarters · All bases'}
              </div>
              <span className={`badge mms-role-badge ${ROLE_BADGE[user?.role] || 'bg-secondary'}`}>
                {user?.role?.replace('_', ' ')}
              </span>
            </div>
            <button type="button" className="btn btn-sm btn-outline-secondary" onClick={handleLogout}>
              Sign out
            </button>
          </div>
        </header>

        <main className="mms-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export function HomeLink() {
  return <Link to="/">Dashboard</Link>
}
