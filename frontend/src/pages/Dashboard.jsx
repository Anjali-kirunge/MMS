import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import { EmptyRow, ErrorAlert, Spinner } from '../components/Feedback.jsx'

const MOVEMENT_TYPES = [
  { key: 'PURCHASE', label: 'Purchases', tone: 'success' },
  { key: 'TRANSFER_IN', label: 'Transfer In', tone: 'info' },
  { key: 'TRANSFER_OUT', label: 'Transfer Out', tone: 'warning' },
]

function StatCard({ label, value, onClick, hint }) {
  return (
    <div className="col-6 col-md-3 col-xl-3">
      <div className={`mms-stat${onClick ? ' clickable' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined}>
        <div className="label">{label}</div>
        <div className="value">{value.toLocaleString()}</div>
        {hint ? <div className="mms-hint">{hint}</div> : null}
      </div>
    </div>
  )
}

export default function Dashboard() {
  const { user, isBaseCommander } = useAuth()
  const { bases, equipmentTypes } = useReferenceData()

  const [filters, setFilters] = useState({
    from: '',
    to: '',
    baseId: user?.baseId ? String(user.baseId) : '',
    equipmentTypeId: '',
  })
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [drilldown, setDrilldown] = useState(null)
  const [drilldownTab, setDrilldownTab] = useState('PURCHASE')
  const [drilldownData, setDrilldownData] = useState({})
  const [drilldownLoading, setDrilldownLoading] = useState(false)
  const [drilldownError, setDrilldownError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.dashboard(filters)
      setData(result)
      setError(null)
    } catch (err) {
      setError(err)
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    load()
  }, [load])

  const totals = data?.totals

  const openDrilldown = () => {
    if (!filters.baseId) {
      setDrilldownError(new Error('Select a base first to drill into net movement details.'))
    }
    setDrilldown(true)
    setDrilldownTab('PURCHASE')
  }

  useEffect(() => {
    if (!drilldown || !filters.baseId) return
    let cancelled = false
    setDrilldownLoading(true)
    api
      .movements({ type: drilldownTab, baseId: filters.baseId, equipmentTypeId: filters.equipmentTypeId, from: filters.from, to: filters.to })
      .then((result) => {
        if (!cancelled) {
          setDrilldownData((prev) => ({ ...prev, [drilldownTab]: result }))
          setDrilldownError(null)
        }
      })
      .catch((err) => !cancelled && setDrilldownError(err))
      .finally(() => !cancelled && setDrilldownLoading(false))
    return () => {
      cancelled = true
    }
  }, [drilldown, drilldownTab, filters])

  const activeLines = drilldownData[drilldownTab]?.lines || []

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Dashboard</h5>
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={load} disabled={loading}>
            Refresh
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            You are viewing data for your assigned base only ({user.baseCode} · {user.baseName}).
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="from">
              From date
            </label>
            <input
              id="from"
              type="date"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => setFilters((f) => ({ ...f, from: e.target.value }))}
            />
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="to">
              To date
            </label>
            <input
              id="to"
              type="date"
              className="form-control form-control-sm"
              value={filters.to}
              onChange={(e) => setFilters((f) => ({ ...f, to: e.target.value }))}
            />
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="base">
              Base
            </label>
            <select
              id="base"
              className="form-select form-select-sm"
              value={filters.baseId}
              disabled={isBaseCommander}
              onChange={(e) => setFilters((f) => ({ ...f, baseId: e.target.value }))}
            >
              <option value="">All bases</option>
              {bases.map((base) => (
                <option key={base.id} value={base.id}>
                  {base.code} · {base.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="equipment">
              Equipment type
            </label>
            <select
              id="equipment"
              className="form-select form-select-sm"
              value={filters.equipmentTypeId}
              onChange={(e) => setFilters((f) => ({ ...f, equipmentTypeId: e.target.value }))}
            >
              <option value="">All equipment</option>
              {equipmentTypes.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.code} · {type.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12 col-md-auto d-flex gap-2">
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => setFilters({ from: '', to: '', baseId: user?.baseId ? String(user.baseId) : '', equipmentTypeId: '' })}
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}

      {loading && !data ? (
        <Spinner label="Loading dashboard…" />
      ) : (
        totals && (
          <>
            <div className="row g-2 mb-3">
              <StatCard label="Opening Balance" value={totals.openingBalance} hint="Brought into the period" />
              <StatCard label="Purchases" value={totals.purchases} hint="Stock received" />
              <StatCard label="Transfer In" value={totals.transferIn} hint="Received from other bases" />
              <StatCard label="Transfer Out" value={totals.transferOut} hint="Sent to other bases" />
              <StatCard
                label="Net Movement"
                value={totals.netMovement}
                hint="Click for details"
                onClick={openDrilldown}
              />
              <StatCard label="Assigned" value={totals.assigned} hint="Issued to personnel" />
              <StatCard label="Expended" value={totals.expended} hint="Written off" />
              <StatCard label="Closing Balance" value={totals.closingBalance} hint="Stock on hand" />
            </div>

            <div className="mms-card">
              <h5 className="mb-2">Breakdown by base and equipment</h5>
              <p className="mms-hint">
                Net Movement = Purchases + Transfer In − Transfer Out · Closing = Opening + Purchases + Transfer In
                − Transfer Out − Assigned − Expended
              </p>
              <div className="table-responsive">
                <table className="table table-sm mms-table">
                  <thead>
                    <tr>
                      <th>Base</th>
                      <th>Equipment</th>
                      <th className="num">Opening</th>
                      <th className="num">Purchases</th>
                      <th className="num">Transfer In</th>
                      <th className="num">Transfer Out</th>
                      <th className="num">Net Movement</th>
                      <th className="num">Assigned</th>
                      <th className="num">Expended</th>
                      <th className="num">Closing</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.rows.length === 0 ? (
                      <EmptyRow colSpan={10} label="No stock movement recorded for these filters." />
                    ) : (
                      data.rows.map((row) => (
                        <tr key={`${row.baseId}-${row.equipmentTypeId}`}>
                          <td>
                            <span className="fw-semibold">{row.baseCode}</span>
                            <div className="mms-hint">{row.baseName}</div>
                          </td>
                          <td>
                            <span className="fw-semibold">{row.equipmentCode}</span>
                            <div className="mms-hint">{row.equipmentName}</div>
                          </td>
                          <td className="num">{row.openingBalance}</td>
                          <td className="num text-success">{row.purchases}</td>
                          <td className="num text-info">{row.transferIn}</td>
                          <td className="num text-warning">{row.transferOut}</td>
                          <td className="num fw-semibold">{row.netMovement}</td>
                          <td className="num">{row.assigned}</td>
                          <td className="num text-danger">{row.expended}</td>
                          <td className="num fw-bold">{row.closingBalance}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                  {data.rows.length > 0 ? (
                    <tfoot className="table-light">
                      <tr className="fw-semibold">
                        <td colSpan={2}>Total</td>
                        <td className="num">{totals.openingBalance}</td>
                        <td className="num">{totals.purchases}</td>
                        <td className="num">{totals.transferIn}</td>
                        <td className="num">{totals.transferOut}</td>
                        <td className="num">{totals.netMovement}</td>
                        <td className="num">{totals.assigned}</td>
                        <td className="num">{totals.expended}</td>
                        <td className="num">{totals.closingBalance}</td>
                      </tr>
                    </tfoot>
                  ) : null}
                </table>
              </div>
            </div>
          </>
        )
      )}

      {drilldown ? (
        <Modal title="Net movement details" onClose={() => setDrilldown(false)} size="modal-lg">
          <div className="mms-hint mb-3">
            Net Movement = Purchases + Transfer In − Transfer Out. These are the underlying transactions for the
            current filters.
          </div>

          {drilldownError && !filters.baseId ? (
            <div className="alert alert-warning py-2">Select a base in the filters to load movement details.</div>
          ) : null}

          <ul className="nav nav-pills mb-3">
            {MOVEMENT_TYPES.map((type) => (
              <li className="nav-item" key={type.key}>
                <button
                  type="button"
                  className={`nav-link ${drilldownTab === type.key ? 'active' : `text-${type.tone}`}`}
                  onClick={() => setDrilldownTab(type.key)}
                >
                  {type.label}
                </button>
              </li>
            ))}
          </ul>

          {drilldownError && filters.baseId ? <ErrorAlert error={drilldownError} /> : null}

          {drilldownLoading ? (
            <Spinner label="Loading movements…" />
          ) : (
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Date</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Equipment</th>
                    <th className="num">Quantity</th>
                  </tr>
                </thead>
                <tbody>
                  {activeLines.length === 0 ? (
                    <EmptyRow colSpan={6} label="No transactions of this type for the selected filters." />
                  ) : (
                    activeLines.map((line) => (
                      <tr key={`${line.movementType}-${line.id}`}>
                        <td className="fw-semibold">{line.referenceNo}</td>
                        <td>{line.movementDate}</td>
                        <td>{line.fromBase}</td>
                        <td>{line.toBase}</td>
                        <td>
                          {line.equipmentCode}
                          <div className="mms-hint">{line.equipmentName}</div>
                        </td>
                        <td className="num">
                          {line.quantity} {line.unit}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </Modal>
      ) : null}
    </>
  )
}
