import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import useReferenceData from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 15
const emptyForm = () => ({ serviceNumber: '', fullName: '', rankTitle: '', baseId: '', contact: '' })

export default function Personnel() {
  const { bases } = useReferenceData()

  const [filters, setFilters] = useState({ baseId: '', q: '' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [actionError, setActionError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.personnelSearch({ ...filters, page, size: PAGE_SIZE })
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

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm())
    setFormError(null)
    setShowForm(true)
  }

  const openEdit = (row) => {
    setEditing(row)
    setForm({
      serviceNumber: row.serviceNumber,
      fullName: row.fullName,
      rankTitle: row.rankTitle || '',
      baseId: String(row.baseId),
      contact: row.contact || '',
    })
    setFormError(null)
    setShowForm(true)
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        serviceNumber: form.serviceNumber,
        fullName: form.fullName,
        rankTitle: form.rankTitle || null,
        baseId: Number(form.baseId),
        contact: form.contact || null,
      }
      if (editing) {
        await api.updatePersonnel(editing.id, payload)
        setFlash({ variant: 'success', message: `Personnel ${form.serviceNumber} updated.` })
      } else {
        await api.createPersonnel(payload)
        setFlash({ variant: 'success', message: `Personnel ${form.serviceNumber} created.` })
      }
      setShowForm(false)
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    if (!confirmDelete) return
    setActionError(null)
    try {
      await api.deletePersonnel(confirmDelete.id)
      setFlash({ variant: 'success', message: `Personnel ${confirmDelete.serviceNumber} deleted.` })
      setConfirmDelete(null)
      load()
    } catch (err) {
      setActionError(err)
    }
  }

  const rows = data?.content || []

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Personnel</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openCreate}>
            Add personnel
          </button>
        </div>

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-4">
            <label className="form-label" htmlFor="per-base">
              Base
            </label>
            <select
              id="per-base"
              className="form-select form-select-sm"
              value={filters.baseId}
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
            <label className="form-label" htmlFor="per-q">
              Search
            </label>
            <input
              id="per-q"
              className="form-control form-control-sm"
              placeholder="Service number, name or rank"
              value={filters.q}
              onChange={(e) => updateFilter('q')(e.target.value)}
            />
          </div>
          <div className="col-12 col-md-4 mms-hint">
            Personnel can only be issued assets at the base they are posted to.
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      {actionError ? <ErrorAlert error={actionError} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading personnel…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Service no.</th>
                    <th>Name</th>
                    <th>Rank</th>
                    <th>Base</th>
                    <th>Contact</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={6} label="No personnel match these filters." />
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td className="fw-semibold">{row.serviceNumber}</td>
                        <td>{row.fullName}</td>
                        <td className="mms-hint">{row.rankTitle || '—'}</td>
                        <td>
                          {row.baseCode}
                          <div className="mms-hint">{row.baseName}</div>
                        </td>
                        <td className="mms-hint">{row.contact || '—'}</td>
                        <td className="text-end text-nowrap">
                          <button type="button" className="btn btn-sm btn-outline-secondary me-1" onClick={() => openEdit(row)}>
                            Edit
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => {
                              setConfirmDelete(row)
                              setActionError(null)
                            }}
                          >
                            Delete
                          </button>
                        </td>
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
          title={editing ? `Edit ${editing.serviceNumber}` : 'Add personnel'}
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="personnel-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save personnel'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="personnel-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-4">
                <label className="form-label" htmlFor="p-service">
                  Service number *
                </label>
                <input
                  id="p-service"
                  className="form-control form-control-sm"
                  value={form.serviceNumber}
                  onChange={(e) => setForm((f) => ({ ...f, serviceNumber: e.target.value }))}
                  required
                  maxLength={40}
                />
              </div>
              <div className="col-md-8">
                <label className="form-label" htmlFor="p-name">
                  Full name *
                </label>
                <input
                  id="p-name"
                  className="form-control form-control-sm"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                  required
                  maxLength={120}
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="p-rank">
                  Rank
                </label>
                <input
                  id="p-rank"
                  className="form-control form-control-sm"
                  value={form.rankTitle}
                  onChange={(e) => setForm((f) => ({ ...f, rankTitle: e.target.value }))}
                  maxLength={60}
                />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="p-base">
                  Base *
                </label>
                <select
                  id="p-base"
                  className="form-select form-select-sm"
                  value={form.baseId}
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
                <label className="form-label" htmlFor="p-contact">
                  Contact
                </label>
                <input
                  id="p-contact"
                  className="form-control form-control-sm"
                  value={form.contact}
                  onChange={(e) => setForm((f) => ({ ...f, contact: e.target.value }))}
                  maxLength={40}
                />
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {confirmDelete ? (
        <Modal
          title="Delete personnel"
          onClose={() => setConfirmDelete(null)}
          size="modal-sm"
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setConfirmDelete(null)}>
                Cancel
              </button>
              <button type="button" className="btn btn-danger" onClick={remove}>
                Delete
              </button>
            </>
          }
        >
          {actionError ? <ErrorAlert error={actionError} /> : null}
          <p className="mb-0">
            Delete <strong>{confirmDelete.fullName}</strong> ({confirmDelete.serviceNumber})? Personnel with
            assignment history cannot be deleted.
          </p>
        </Modal>
      ) : null}
    </>
  )
}
