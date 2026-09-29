import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 10
const today = () => new Date().toISOString().slice(0, 10)
const REASONS = ['Training', 'Operations', 'Maintenance', 'Loss / damage', 'Consumed', 'Other']

const emptyForm = (baseId) => ({
  baseId: baseId ? String(baseId) : '',
  equipmentTypeId: '',
  quantity: 1,
  expendedDate: today(),
  reason: REASONS[0],
  remarks: '',
})

export default function Expenditures() {
  const { user, isBaseCommander } = useAuth()
  const { bases, equipmentTypes } = useReferenceData()

  const [filters, setFilters] = useState({
    baseId: user?.baseId ? String(user.baseId) : '',
    equipmentTypeId: '',
    from: '',
    to: '',
  })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)

  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(() => emptyForm(user?.baseId))
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.expenditures({ ...filters, page, size: PAGE_SIZE })
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

  const openForm = () => {
    setForm(emptyForm(user?.baseId))
    setFormError(null)
    setShowForm(true)
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        baseId: Number(form.baseId),
        equipmentTypeId: Number(form.equipmentTypeId),
        quantity: Number(form.quantity),
        expendedDate: form.expendedDate,
        reason: form.reason,
        remarks: form.remarks || null,
      }
      const created = await api.createExpenditure(payload)
      setShowForm(false)
      setPage(0)
      setFlash({
        variant: 'success',
        message: `${created.quantity} × ${created.equipmentCode} expended at ${created.baseCode} `
          + `(${created.reason}). On-hand quantity reduced.`,
      })
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const rows = data?.content || []
  const totalExpended = rows.reduce((sum, row) => sum + (row.quantity || 0), 0)

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Expenditures</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openForm}>
            Record expenditure
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            Showing and recording expenditures for {user.baseCode} · {user.baseName} only.
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="exp-base">
              Base
            </label>
            <select
              id="exp-base"
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
            <label className="form-label" htmlFor="exp-equipment">
              Equipment type
            </label>
            <select
              id="exp-equipment"
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
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="exp-from">
              From
            </label>
            <input
              id="exp-from"
              type="date"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => updateFilter('from')(e.target.value)}
            />
          </div>
          <div className="col-6 col-md-2">
            <label className="form-label" htmlFor="exp-to">
              To
            </label>
            <input
              id="exp-to"
              type="date"
              className="form-control form-control-sm"
              value={filters.to}
              onChange={(e) => updateFilter('to')(e.target.value)}
            />
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading expenditures…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Base</th>
                    <th>Equipment</th>
                    <th className="num">Qty</th>
                    <th>Reason</th>
                    <th>Remarks</th>
                    <th>Recorded by</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={7} label="No expenditures match these filters." />
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td>{row.expendedDate}</td>
                        <td>
                          <span className="fw-semibold">{row.baseCode}</span>
                          <div className="mms-hint">{row.baseName}</div>
                        </td>
                        <td>
                          {row.equipmentCode}
                          <div className="mms-hint">{row.equipmentName}</div>
                        </td>
                        <td className="num">
                          {row.quantity} {row.unit}
                        </td>
                        <td>{row.reason}</td>
                        <td className="mms-hint">{row.remarks || '—'}</td>
                        <td className="mms-hint">{row.createdBy || '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
              <span className="mms-hint">
                Expended on this page: <strong>{totalExpended.toLocaleString()}</strong> units
              </span>
              <Pagination page={page} size={PAGE_SIZE} totalElements={data?.totalElements || 0} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      {showForm ? (
        <Modal
          title="Record expenditure"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="expenditure-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save expenditure'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="expenditure-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-6">
                <label className="form-label" htmlFor="e-base">
                  Base *
                </label>
                <select
                  id="e-base"
                  className="form-select form-select-sm"
                  value={form.baseId}
                  disabled={isBaseCommander}
                  onChange={(e) => setForm((f) => ({ ...f, baseId: e.target.value }))}
                  required
                >
                  <option value="">Select a base</option>
                  {bases.map((base) => (
                    <option key={base.id} value={base.id}>
                      {base.code} · {base.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="e-equipment">
                  Equipment type *
                </label>
                <select
                  id="e-equipment"
                  className="form-select form-select-sm"
                  value={form.equipmentTypeId}
                  onChange={(e) => setForm((f) => ({ ...f, equipmentTypeId: e.target.value }))}
                  required
                >
                  <option value="">Select equipment</option>
                  {equipmentTypes.map((type) => (
                    <option key={type.id} value={type.id}>
                      {type.code} · {type.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="e-qty">
                  Quantity *
                </label>
                <input
                  id="e-qty"
                  type="number"
                  min="1"
                  className="form-control form-control-sm text-end"
                  value={form.quantity}
                  onChange={(e) => setForm((f) => ({ ...f, quantity: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="e-date">
                  Expended date *
                </label>
                <input
                  id="e-date"
                  type="date"
                  className="form-control form-control-sm"
                  value={form.expendedDate}
                  onChange={(e) => setForm((f) => ({ ...f, expendedDate: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="e-reason">
                  Reason *
                </label>
                <select
                  id="e-reason"
                  className="form-select form-select-sm"
                  value={form.reason}
                  onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
                  required
                >
                  {REASONS.map((reason) => (
                    <option key={reason} value={reason}>
                      {reason}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="e-remarks">
                  Remarks
                </label>
                <input
                  id="e-remarks"
                  className="form-control form-control-sm"
                  value={form.remarks}
                  onChange={(e) => setForm((f) => ({ ...f, remarks: e.target.value }))}
                />
              </div>
            </div>
            <p className="mms-hint mt-3 mb-0">
              Recording an expenditure permanently reduces the on-hand quantity of the selected base. Stocks can never
              fall below zero.
            </p>
          </form>
        </Modal>
      ) : null}
    </>
  )
}
