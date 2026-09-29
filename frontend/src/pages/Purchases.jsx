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
  baseId: baseId ? String(baseId) : '',
  supplier: '',
  invoiceNo: '',
  purchaseDate: today(),
  remarks: '',
  items: [{ equipmentTypeId: '', quantity: 1, unitCost: '' }],
})

export default function Purchases() {
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
      const result = await api.purchases({ ...filters, page, size: PAGE_SIZE })
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

  const setItem = (index, key, value) => {
    setForm((f) => {
      const items = f.items.map((item, i) => (i === index ? { ...item, [key]: value } : item))
      return { ...f, items }
    })
  }

  const addItem = () =>
    setForm((f) => ({ ...f, items: [...f.items, { equipmentTypeId: '', quantity: 1, unitCost: '' }] }))

  const removeItem = (index) =>
    setForm((f) => ({ ...f, items: f.items.filter((_, i) => i !== index) }))

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        baseId: Number(form.baseId),
        supplier: form.supplier,
        invoiceNo: form.invoiceNo || null,
        purchaseDate: form.purchaseDate,
        remarks: form.remarks || null,
        items: form.items
          .filter((item) => item.equipmentTypeId)
          .map((item) => ({
            equipmentTypeId: Number(item.equipmentTypeId),
            quantity: Number(item.quantity),
            unitCost: item.unitCost === '' ? 0 : Number(item.unitCost),
          })),
      }
      const created = await api.createPurchase(payload)
      setShowForm(false)
      setPage(0)
      setFlash({
        variant: 'success',
        message: `Purchase ${created.referenceNo} recorded at ${created.baseCode}. Stock has been increased.`,
      })
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const rows = data?.content || []
  const estimatedTotal = form.items.reduce((sum, item) => {
    const qty = Number(item.quantity) || 0
    const cost = item.unitCost === '' ? 0 : Number(item.unitCost) || 0
    return sum + qty * cost
  }, 0)

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Purchases</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openForm}>
            Record purchase
          </button>
        </div>

        {isBaseCommander ? (
          <div className="alert alert-info py-2 mms-hint">
            Purchases are limited to your assigned base ({user.baseCode} · {user.baseName}).
          </div>
        ) : null}

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="pur-base">
              Base
            </label>
            <select
              id="pur-base"
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
            <label className="form-label" htmlFor="pur-from">
              From
            </label>
            <input
              id="pur-from"
              type="date"
              className="form-control form-control-sm"
              value={filters.from}
              onChange={(e) => updateFilter('from')(e.target.value)}
            />
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="pur-to">
              To
            </label>
            <input
              id="pur-to"
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
          <Spinner label="Loading purchases…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Date</th>
                    <th>Base</th>
                    <th>Supplier</th>
                    <th>Invoice</th>
                    <th className="num">Total cost</th>
                    <th>Recorded by</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={8} label="No purchases match these filters." />
                  ) : (
                    rows.map((row) => (
                      <Fragment key={row.id}>
                        <tr>
                          <td className="fw-semibold">{row.referenceNo}</td>
                          <td>{row.purchaseDate}</td>
                          <td>{row.baseCode}</td>
                          <td>{row.supplier}</td>
                          <td className="mms-hint">{row.invoiceNo || '—'}</td>
                          <td className="num">{Number(row.totalCost).toLocaleString()}</td>
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
                            <td colSpan={8} className="bg-light">
                              <table className="table table-sm mb-0 mms-table">
                                <thead>
                                  <tr>
                                    <th>Equipment</th>
                                    <th className="num">Quantity</th>
                                    <th className="num">Unit cost</th>
                                    <th className="num">Line total</th>
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
                                      <td className="num">{Number(item.unitCost).toLocaleString()}</td>
                                      <td className="num">{Number(item.lineTotal).toLocaleString()}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                              {row.remarks ? <div className="mms-hint mt-2">Remarks: {row.remarks}</div> : null}
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
          title="Record purchase"
          onClose={() => setShowForm(false)}
          size="modal-lg"
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="purchase-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save purchase'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="purchase-form" onSubmit={submit}>
            <div className="row g-2 mb-3">
              <div className="col-md-4">
                <label className="form-label" htmlFor="f-base">
                  Base *
                </label>
                <select
                  id="f-base"
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
              <div className="col-md-4">
                <label className="form-label" htmlFor="f-supplier">
                  Supplier *
                </label>
                <input
                  id="f-supplier"
                  className="form-control form-control-sm"
                  value={form.supplier}
                  onChange={(e) => setForm((f) => ({ ...f, supplier: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="f-invoice">
                  Invoice number
                </label>
                <input
                  id="f-invoice"
                  className="form-control form-control-sm"
                  value={form.invoiceNo}
                  onChange={(e) => setForm((f) => ({ ...f, invoiceNo: e.target.value }))}
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="f-date">
                  Purchase date *
                </label>
                <input
                  id="f-date"
                  type="date"
                  className="form-control form-control-sm"
                  value={form.purchaseDate}
                  onChange={(e) => setForm((f) => ({ ...f, purchaseDate: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-8">
                <label className="form-label" htmlFor="f-remarks">
                  Remarks
                </label>
                <input
                  id="f-remarks"
                  className="form-control form-control-sm"
                  value={form.remarks}
                  onChange={(e) => setForm((f) => ({ ...f, remarks: e.target.value }))}
                />
              </div>
            </div>

            <div className="d-flex justify-content-between align-items-center mb-2">
              <h6 className="mb-0">Line items</h6>
              <button type="button" className="btn btn-sm btn-outline-primary" onClick={addItem}>
                Add line
              </button>
            </div>

            <div className="table-responsive">
              <table className="table table-sm align-middle mms-table">
                <thead>
                  <tr>
                    <th style={{ width: '55%' }}>Equipment type</th>
                    <th className="num">Quantity</th>
                    <th className="num">Unit cost</th>
                    <th className="num">Line total</th>
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
                      <td className="num" style={{ width: 110 }}>
                        <input
                          type="number"
                          min="1"
                          className="form-control form-control-sm text-end"
                          value={item.quantity}
                          onChange={(e) => setItem(index, 'quantity', e.target.value)}
                          required
                        />
                      </td>
                      <td className="num" style={{ width: 130 }}>
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          className="form-control form-control-sm text-end"
                          value={item.unitCost}
                          onChange={(e) => setItem(index, 'unitCost', e.target.value)}
                        />
                      </td>
                      <td className="num">
                        {((Number(item.quantity) || 0) * (Number(item.unitCost) || 0)).toLocaleString()}
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
                <tfoot className="table-light">
                  <tr>
                    <th colSpan={3} className="text-end">
                      Estimated total
                    </th>
                    <th className="num">{estimatedTotal.toLocaleString()}</th>
                    <th />
                  </tr>
                </tfoot>
              </table>
            </div>
            <p className="mms-hint mb-0">
              Saving increases the on-hand quantity for every line item at the selected base.
            </p>
          </form>
        </Modal>
      ) : null}
    </>
  )
}
