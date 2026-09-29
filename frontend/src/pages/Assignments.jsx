import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useReferenceData, usePersonnel } from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 10
const today = () => new Date().toISOString().slice(0, 10)

const emptyForm = (baseId) => ({
  baseId: baseId ? String(baseId) : '',
  personnelId: '',
  equipmentTypeId: '',
  quantity: 1,
  assignedDate: today(),
  remarks: '',
})

export default function Assignments() {
  const { user, isBaseCommander } = useAuth()
  const { bases, equipmentTypes } = useReferenceData()
  const { personnel } = usePersonnel()

  const [filters, setFilters] = useState({
    baseId: user?.baseId ? String(user.baseId) : '',
    equipmentTypeId: '',
    personnelId: '',
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
      const result = await api.assignments({ ...filters, page, size: PAGE_SIZE })
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
        personnelId: Number(form.personnelId),
        equipmentTypeId: Number(form.equipmentTypeId),
        quantity: Number(form.quantity),
        assignedDate: form.assignedDate,
        remarks: form.remarks || null,
      }
      const created = await api.createAssignment(payload)
      setShowForm(false)
      setPage(0)
      setFlash({
        variant: 'success',
        message: `${created.quantity} × ${created.equipmentCode} assigned to ${created.personnelName} `
          + `(${created.personnelServiceNumber}) at ${created.baseCode}. On-hand quantity reduced.`,
      })
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const basePersonnel = form.baseId ? personnel.filter((p) => String(p.baseId) === form.baseId) : personnel
  const rows = data?.content || []
  const totalAssigned = rows.reduce((sum, row) => sum + (row.quantity || 0), 0)

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Assignments</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openForm}>
            Assign asset
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            Showing and issuing assets for {user.baseCode} · {user.baseName} only.
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="asg-base">
              Base
            </label>
            <select
              id="asg-base"
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
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="asg-equipment">
              Equipment
            </label>
            <select
              id="asg-equipment"
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
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="asg-personnel">
              Personnel
            </label>
            <select
              id="asg-personnel"
              className="form-select form-select-sm"
              value={filters.personnelId}
              onChange={(e) => updateFilter('personnelId')(e.target.value)}
            >
              <option value="">All personnel</option>
              {personnel.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.serviceNumber} · {p.fullName}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-1">
            <label className="form-label" htmlFor="asg-from">
              From
            </label>
            <input
              id="asg-from"
              type="date"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => updateFilter('from')(e.target.value)}
            />
          </div>
          <div className="col-6 col-md-1">
            <label className="form-label" htmlFor="asg-to">
              To
            </label>
            <input
              id="asg-to"
              type="date"
              className="form-control form-control-sm"
              value={filters.to}
              onChange={(e) => updateFilter('to')(e.target.value)}
            />
          </div>
          <div className="col-12 col-md-1 text-md-end">
            <span className="mms-hint">
              Page total <strong>{totalAssigned}</strong>
            </span>
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading assignments…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Base</th>
                    <th>Personnel</th>
                    <th>Equipment</th>
                    <th className="num">Qty</th>
                    <th>Assigned</th>
                    <th>Issued by</th>
                    <th>Remarks</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={7} label="No assignments match these filters." />
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td>
                          <span className="fw-semibold">{row.baseCode}</span>
                          <div className="mms-hint">{row.baseName}</div>
                        </td>
                        <td>
                          <span className="fw-semibold">{row.personnelName}</span>
                          <div className="mms-hint">{row.personnelServiceNumber}</div>
                        </td>
                        <td>
                          {row.equipmentCode}
                          <div className="mms-hint">{row.equipmentName}</div>
                        </td>
                        <td className="num">
                          {row.quantity} {row.unit}
                        </td>
                        <td>{row.assignedDate}</td>
                        <td className="mms-hint">{row.createdBy || '—'}</td>
                        <td className="mms-hint">{row.remarks || '—'}</td>
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

      {showForm ? (
        <Modal
          title="Assign asset"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="assignment-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Confirm assignment'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="assignment-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-6">
                <label className="form-label" htmlFor="a-base">
                  Base *
                </label>
                <select
                  id="a-base"
                  className="form-select form-select-sm"
                  value={form.baseId}
                  disabled={isBaseCommander}
                  onChange={(e) => setForm((f) => ({ ...f, baseId: e.target.value, personnelId: '' }))}
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
                <label className="form-label" htmlFor="a-personnel">
                  Personnel *
                </label>
                <select
                  id="a-personnel"
                  className="form-select form-select-sm"
                  value={form.personnelId}
                  onChange={(e) => setForm((f) => ({ ...f, personnelId: e.target.value }))}
                  required
                >
                  <option value="">Select personnel</option>
                  {basePersonnel.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.serviceNumber} · {p.fullName} ({p.rankTitle})
                    </option>
                  ))}
                </select>
                <div className="form-text">Only personnel posted to the selected base can be issued assets.</div>
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="a-equipment">
                  Equipment type *
                </label>
                <select
                  id="a-equipment"
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
              <div className="col-md-3">
                <label className="form-label" htmlFor="a-qty">
                  Quantity *
                </label>
                <input
                  id="a-qty"
                  type="number"
                  min="1"
                  className="form-control form-control-sm text-end"
                  value={form.quantity}
                  onChange={(e) => setForm((f) => ({ ...f, quantity: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-3">
                <label className="form-label" htmlFor="a-date">
                  Assigned date *
                </label>
                <input
                  id="a-date"
                  type="date"
                  className="form-control form-control-sm"
                  value={form.assignedDate}
                  onChange={(e) => setForm((f) => ({ ...f, assignedDate: e.target.value }))}
                  required
                />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="a-remarks">
                  Remarks
                </label>
                <input
                  id="a-remarks"
                  className="form-control form-control-sm"
                  value={form.remarks}
                  onChange={(e) => setForm((f) => ({ ...f, remarks: e.target.value }))}
                />
              </div>
            </div>
            <p className="mms-hint mt-3 mb-0">
              Assigning reduces the on-hand quantity of the selected base. The record is permanent and is reflected in
              the dashboard Assigned figure.
            </p>
          </form>
        </Modal>
      ) : null}
    </>
  )
}
