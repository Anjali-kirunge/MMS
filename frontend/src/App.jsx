import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './context/AuthContext.jsx'
import Layout from './components/Layout.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Inventory from './pages/Inventory.jsx'
import Purchases from './pages/Purchases.jsx'
import Transfers from './pages/Transfers.jsx'
import Assignments from './pages/Assignments.jsx'
import Expenditures from './pages/Expenditures.jsx'
import AuditLogs from './pages/AuditLogs.jsx'
import Users from './pages/Users.jsx'
import Personnel from './pages/Personnel.jsx'
import Bases from './pages/Bases.jsx'
import EquipmentTypes from './pages/EquipmentTypes.jsx'

const RequireAuth = ({ children }) => {
  const { token } = useAuth()
  const location = useLocation()
  return token ? children : <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
}

const RequireRole = ({ roles, children }) => {
  const { user } = useAuth()
  return user && roles.includes(user.role) ? children : <Navigate to="/" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Dashboard />} />
        <Route path="/inventory" element={<Inventory />} />
        <Route
          path="/purchases"
          element={
            <RequireRole roles={['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER']}>
              <Purchases />
            </RequireRole>
          }
        />
        <Route
          path="/transfers"
          element={
            <RequireRole roles={['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER']}>
              <Transfers />
            </RequireRole>
          }
        />
        <Route
          path="/assignments"
          element={
            <RequireRole roles={['ADMIN', 'BASE_COMMANDER']}>
              <Assignments />
            </RequireRole>
          }
        />
        <Route
          path="/expenditures"
          element={
            <RequireRole roles={['ADMIN', 'BASE_COMMANDER']}>
              <Expenditures />
            </RequireRole>
          }
        />
        <Route path="/audit-logs" element={<AuditLogs />} />
        <Route
          path="/personnel"
          element={
            <RequireRole roles={['ADMIN']}>
              <Personnel />
            </RequireRole>
          }
        />
        <Route
          path="/users"
          element={
            <RequireRole roles={['ADMIN']}>
              <Users />
            </RequireRole>
          }
        />
        <Route
          path="/bases"
          element={
            <RequireRole roles={['ADMIN']}>
              <Bases />
            </RequireRole>
          }
        />
        <Route
          path="/equipment-types"
          element={
            <RequireRole roles={['ADMIN']}>
              <EquipmentTypes />
            </RequireRole>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
