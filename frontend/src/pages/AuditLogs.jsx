import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 20
const ACTIONS = ['LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', 'PASSWORD_RESET', 'SEED']

const badgeFor = (action) => {
  if (action === 'LOGIN' || action === 'LOGOUT') return 'text-bg-success'
  if (action === 'LOGIN_FAILED') return 'text-bg-danger'
  if (action === 'CREATE' || action === 'SEED') return 'text-bg-primary'
  if (action === 'UPDATE' || action === 'STATUS_CHANGE') return 'text-bg-warning'
  if (action === 'DELETE') return 'text-bg-dark'
  return 'text-bg-secondary'
}

export default function AuditLogs() {
  const { isBaseCommander } = useAuth()
  const { bases } = useReferenceData()

  const [filters, setFilters] = useState({ entity: '', action: '', baseId: '', from: '', to: '' })
  const [entities, setEntities] = useState([])
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .auditEntities()
      .then(setEntities)
      .catch(() => setEntities([]))
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.auditLogs({ ...filters, page, size: PAGE_SIZE })
      setData(result)
      setError(null)
    } catch (err) {
      setError(err)
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [filters, page])

  useEffect(() => {
    load()
  }, [load])

  const updateFilter = (key) => (value) => {
    setPage(0)
    setFilters((f) => ({ ...f, [key]: value }))
  }

  const rows = data?.content || []

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Audit logs</h5>
          <span className="badge text-bg-secondary">Append-only · read only</span>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            Audit entries are filtered to your assigned base.
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="aud-entity">
              Entity
            </label>
            <select
              id="aud-entity"
              className="form-select form-select-sm"
              value={filters.entity}
              onChange={(e) => updateFilter('entity')(e.target.value)}
            >
              <option value="">All entities</option>
              {entities.map((entity) => (
                <option key={entity} value={entity}>
                  {entity}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="aud-action">
              Action
            </label>
            <select
              id="aud-action"
              className="form-select form-select-sm"
              value={filters.action}
              onChange={(e) => updateFilter('action')(e.target.value)}
            >
              <option value="">All actions</option>
              {ACTIONS.map((action) => (
                <option key={action} value={action}>
                  {action}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="aud-base">
              Base
            </label>
            <select
              id="aud-base"
              className="form-select form-select-sm"
              value={filters.baseId}
              disabled={isBaseCommander}
              onChange={(e) => updateFilter('baseId')(e.target.value)}
            >
              <option value="">All bases</option>
              {bases.map((base) => (
                <option key={base.id} value={base.id}>
                  {base.code} · {base.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="aud-from">
              From
            </label>
            <input
              id="aud-from"
              type="datetime-local"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => updateFilter('from')(e.target.value)}
            />
          </div>
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="aud-to">
              To
            </label>
            <input
              id="aud-to"
              type="datetime-local"
              className="form-control form-control-sm"
              value={filters.to}
              onChange={(e) => updateFilter('to')(e.target.value)}
            />
          </div>
          <div className="col-12 col-md-1 text-md-end">
            <button type="button" className="btn btn-sm btn-outline-secondary" onClick={load} disabled={loading}>
              Refresh
            </button>
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading audit logs…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>User</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Base</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={6} label="No audit entries match these filters." />
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td className="mms-hint text-nowrap">{row.createdAt?.replace('T', ' ')}</td>
                        <td>
                          <span className="fw-semibold">{row.username || 'anonymous'}</span>
                        </td>
                        <td>
                          <span className={`badge ${badgeFor(row.action)}`}>{row.action}</span>
                        </td>
                        <td>
                          {row.entity}
                          {row.entityId ? <span className="mms-hint"> #{row.entityId}</span> : null}
                        </td>
                        <td>{row.baseCode || '—'}</td>
                        <td className="mms-hint">{row.description}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <Pagination page={page} size={PAGE_SIZE} totalElements={data?.totalElements || 0} onPageChange={setPage} />
          </>
        )}
      </div>
    </>
  )
}
