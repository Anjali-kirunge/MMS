import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 15

export default function Inventory() {
  const { user, isBaseCommander } = useAuth()
  const { bases, equipmentTypes } = useReferenceData()

  const [filters, setFilters] = useState({ baseId: user?.baseId ? String(user.baseId) : '', equipmentTypeId: '' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.inventory({ ...filters, page, size: PAGE_SIZE })
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
  const totalQty = rows.reduce((sum, row) => sum + (row.onHandQuantity || 0), 0)

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Inventory</h5>
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary"
            onClick={() => {
              setFlash({ variant: 'success', message: 'Inventory refreshed from the database.' })
              load()
            }}
            disabled={loading}
          >
            Refresh
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            Showing your assigned base only ({user.baseCode} · {user.baseName}).
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="inv-base">
              Base
            </label>
            <select
              id="inv-base"
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
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="inv-equipment">
              Equipment type
            </label>
            <select
              id="inv-equipment"
              className="form-select form-select-sm"
              value={filters.equipmentTypeId}
              onChange={(e) => updateFilter('equipmentTypeId')(e.target.value)}
            >
              <option value="">All equipment</option>
              {equipmentTypes.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.code} · {type.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12 col-md-4 text-md-end">
            <span className="mms-hint">
              On this page: <strong>{totalQty.toLocaleString()}</strong> units across {rows.length} stock row(s)
            </span>
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading inventory…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Base</th>
                    <th>Equipment</th>
                    <th>Category</th>
                    <th className="num">Opening balance</th>
                    <th className="num">On hand</th>
                    <th className="num">Unit</th>
                    <th>Last updated</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={7} label="No stock rows match these filters." />
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td>
                          <span className="fw-semibold">{row.baseCode}</span>
                          <div className="mms-hint">{row.baseName}</div>
                        </td>
                        <td>
                          <span className="fw-semibold">{row.equipmentCode}</span>
                          <div className="mms-hint">{row.equipmentName}</div>
                        </td>
                        <td>{row.category}</td>
                        <td className="num">{row.openingBalance}</td>
                        <td className="num fw-bold">{row.onHandQuantity}</td>
                        <td className="num mms-hint">{row.unit}</td>
                        <td className="mms-hint">{row.updatedAt?.replace('T', ' ')}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              size={PAGE_SIZE}
              totalElements={data?.totalElements || 0}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </>
  )
}
