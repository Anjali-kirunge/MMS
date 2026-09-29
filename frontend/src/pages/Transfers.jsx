import { Fragment, useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 10
const today = () => new Date().toISOString().slice(0, 10)

const emptyForm = (baseId) => ({
  sourceBaseId: baseId ? String(baseId) : '',
  destinationBaseId: '',
  transferDate: today(),
  remarks: '',
  items: [{ equipmentTypeId: '', quantity: 1 }],
})

export default function Transfers() {
  const { user, isBaseCommander } = useAuth()
  const { bases, equipmentTypes } = useReferenceData()

  const [filters, setFilters] = useState({ baseId: user?.baseId ? String(user.baseId) : '', from: '', to: '' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)
  const [expanded, setExpanded] = useState(null)

  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(() => emptyForm(user?.baseId))
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.transfers({ ...filters, page, size: PAGE_SIZE })
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

  const setItem = (index, key, value) =>
    setForm((f) => ({ ...f, items: f.items.map((item, i) => (i === index ? { ...item, [key]: value } : item)) }))

  const addItem = () =>
    setForm((f) => ({ ...f, items: [...f.items, { equipmentTypeId: '', quantity: 1 }] }))

  const removeItem = (index) => setForm((f) => ({ ...f, items: f.items.filter((_, i) => i !== index) }))

  const sameBase = form.sourceBaseId && form.sourceBaseId === form.destinationBaseId

  const submit = async (event) => {
    event.preventDefault()
    if (sameBase) {
      setFormError(new Error('Source and destination base must be different.'))
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        sourceBaseId: Number(form.sourceBaseId),
        destinationBaseId: Number(form.destinationBaseId),
        transferDate: form.transferDate,
        remarks: form.remarks || null,
        items: form.items
          .filter((item) => item.equipmentTypeId)
          .map((item) => ({
            equipmentTypeId: Number(item.equipmentTypeId),
            quantity: Number(item.quantity),
          })),
      }
      const created = await api.createTransfer(payload)
      setShowForm(false)
      setPage(0)
      setFlash({
        variant: 'success',
        message: `Transfer ${created.referenceNo} completed: ${created.sourceBaseCode} → ${created.destinationBaseCode}.`,
      })
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const rows = data?.content || []

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Transfers</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openForm}>
            New transfer
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            You can initiate transfers out of {user.baseCode} · {user.baseName} only.
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="trf-base">
              Base
            </label>
            <select
              id="trf-base"
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
            <label className="form-label" htmlFor="trf-from">
              From
            </label>
            <input
              id="trf-from"
              type="date"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => updateFilter('from')(e.target.value)}
            />
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="trf-to">
              To
            </label>
            <input
              id="trf-to"
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
          <Spinner label="Loading transfers…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Date</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Remarks</th>
                    <th>Recorded by</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={7} label="No transfers match these filters." />
                  ) : (
                    rows.map((row) => (
                      <Fragment key={row.id}>
                        <tr>
                          <td className="fw-semibold">{row.referenceNo}</td>
                          <td>{row.transferDate}</td>
                          <td>{row.sourceBaseCode}</td>
                          <td>{row.destinationBaseCode}</td>
                          <td className="mms-hint">{row.remarks || '—'}</td>
                          <td className="mms-hint">{row.createdBy || '—'}</td>
                          <td className="text-end">
                            <button
                              type="button"
                              className="btn btn-sm btn-link p-0"
                              onClick={() => setExpanded(expanded === row.id ? null : row.id)}
                            >
                              {expanded === row.id ? 'Hide items' : `Items (${row.items.length})`}
                            </button>
                          </td>
                        </tr>
                        {expanded === row.id ? (
                          <tr>
                            <td colSpan={7} className="bg-light">
                              <table className="table table-sm mb-0 mms-table">
                                <thead>
                                  <tr>
                                    <th>Equipment</th>
                                    <th className="num">Quantity</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {row.items.map((item) => (
                                    <tr key={item.id}>
                                      <td>
                                        {item.equipmentCode} · {item.equipmentName}
                                      </td>
                                      <td className="num">
                                        {item.quantity} {item.unit}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </td>
                          </tr>
                        ) : null}
                      </Fragment>
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
          title="New transfer"
          onClose={() => setShowForm(false)}
          size="modal-lg"
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button
                type="submit"
                form="transfer-form"
                className="btn btn-primary"
                disabled={saving || sameBase}
              >
                {saving ? 'Transferring…' : 'Confirm transfer'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          {sameBase ? (
            <div className="alert alert-warning py-2">
              Source and destination base must be different.
            </div>
          ) : null}
          <form id="transfer-form" onSubmit={submit}>
            <div className="row g-2 mb-3">
              <div className="col-md-4">
                <label className="form-label" htmlFor="t-source">
                  Source base *
                </label>
                <select
                  id="t-source"
                  className="form-select form-select-sm"
                  value={form.sourceBaseId}
                  disabled={isBaseCommander}
                  onChange={(e) => setForm((f) => ({ ...f, sourceBaseId: e.target.value }))}
                  required
                >
                  <option value="">Select source base</option>
                  {bases.map((base) => (
                    <option key={base.id} value={base.id}>
                      {base.code} · {base.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="t-dest">
                  Destination base *
                </label>
                <select
                  id="t-dest"
                  className="form-select form-select-sm"
                  value={form.destinationBaseId}
                  onChange={(e) => setForm((f) => ({ ...f, destinationBaseId: e.target.value }))}
                  required
                >
                  <option value="">Select destination base</option>
                  {bases
                    .filter((base) => String(base.id) !== form.sourceBaseId)
                    .map((base) => (
                      <option key={base.id} value={base.id}>
                        {base.code} · {base.name}
                      </option>
                    ))}
                </select>
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="t-date">
                  Transfer date *
                </label>
                <input
                  id="t-date"
                  type="date"
                  className="form-control form-control-sm"
                  value={form.transferDate}
                  onChange={(e) => setForm((f) => ({ ...f, transferDate: e.target.value }))}
                  required
                />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="t-remarks">
                  Remarks
                </label>
                <input
                  id="t-remarks"
                  className="form-control form-control-sm"
                  value={form.remarks}
                  onChange={(e) => setForm((f) => ({ ...f, remarks: e.target.value }))}
                />
              </div>
            </div>

            <div className="d-flex justify-content-between align-items-center mb-2">
              <h6 className="mb-0">Items to move</h6>
              <button type="button" className="btn btn-sm btn-outline-primary" onClick={addItem}>
                Add line
              </button>
            </div>

            <div className="table-responsive">
              <table className="table table-sm align-middle mms-table">
                <thead>
                  <tr>
                    <th>Equipment type</th>
                    <th className="num" style={{ width: 120 }}>
                      Quantity
                    </th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {form.items.map((item, index) => (
                    <tr key={index}>
                      <td>
                        <select
                          className="form-select form-select-sm"
                          value={item.equipmentTypeId}
                          onChange={(e) => setItem(index, 'equipmentTypeId', e.target.value)}
                          required
                        >
                          <option value="">Select equipment</option>
                          {equipmentTypes.map((type) => (
                            <option key={type.id} value={type.id}>
                              {type.code} · {type.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="num">
                        <input
                          type="number"
                          min="1"
                          className="form-control form-control-sm text-end"
                          value={item.quantity}
                          onChange={(e) => setItem(index, 'quantity', e.target.value)}
                          required
                        />
                      </td>
                      <td className="text-end">
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => removeItem(index)}
                          disabled={form.items.length === 1}
                        >
                          ✕
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mms-hint mb-0">
              Stock is deducted from the source base and added to the destination base in a single transaction. If any
              line would take the source below zero the whole transfer is rejected and rolled back.
            </p>
          </form>
        </Modal>
      ) : null}
    </>
  )
}
