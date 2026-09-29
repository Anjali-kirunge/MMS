import { useEffect, useState } from 'react'
import { api } from '../api/client.js'
import Modal from '../components/Modal.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const emptyForm = () => ({ code: '', name: '', category: '', unit: 'pcs', description: '' })

export default function EquipmentTypes() {
  const [rows, setRows] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)
  const [query, setQuery] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [actionError, setActionError] = useState(null)

  const load = () => {
    setLoading(true)
    return api
      .equipmentTypes()
      .then((result) => {
        setRows(result)
        setError(null)
      })
      .catch((err) => {
        setError(err)
        setRows(null)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm())
    setFormError(null)
    setShowForm(true)
  }

  const openEdit = (row) => {
    setEditing(row)
    setForm({
      code: row.code,
      name: row.name,
      category: row.category,
      unit: row.unit || 'pcs',
      description: row.description || '',
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
        code: form.code,
        name: form.name,
        category: form.category,
        unit: form.unit || null,
        description: form.description || null,
      }
      if (editing) {
        await api.updateEquipmentType(editing.id, payload)
        setFlash({ variant: 'success', message: `Equipment type ${form.code} updated.` })
      } else {
        await api.createEquipmentType(payload)
        setFlash({ variant: 'success', message: `Equipment type ${form.code} created.` })
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
      await api.deleteEquipmentType(confirmDelete.id)
      setFlash({ variant: 'success', message: `Equipment type ${confirmDelete.code} deleted.` })
      setConfirmDelete(null)
      load()
    } catch (err) {
      setActionError(err)
    }
  }

  const visible = (rows || []).filter((row) => {
    const needle = query.trim().toLowerCase()
    if (!needle) return true
    return [row.code, row.name, row.category, row.description]
      .filter(Boolean)
      .some((field) => field.toLowerCase().includes(needle))
  })

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">Equipment types</h5>
          <div className="d-flex gap-2">
            <input
              className="form-control form-control-sm"
              style={{ width: 200 }}
              placeholder="Filter loaded list…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Filter equipment types"
            />
            <button type="button" className="btn btn-sm btn-primary" onClick={openCreate}>
              New type
            </button>
          </div>
        </div>
        <p className="mms-hint mb-0">
          A type that already appears in stock or transactions cannot be deleted; disable it by removing it from future
          use instead.
        </p>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      {actionError ? <ErrorAlert error={actionError} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !rows ? (
          <Spinner label="Loading equipment types…" />
        ) : (
          <div className="table-responsive">
            <table className="table table-sm mms-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Category</th>
                  <th>Unit</th>
                  <th>Description</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.length === 0 ? (
                  <EmptyRow colSpan={6} label="No equipment types match this filter." />
                ) : (
                  visible.map((row) => (
                    <tr key={row.id}>
                      <td className="fw-semibold">{row.code}</td>
                      <td>{row.name}</td>
                      <td>{row.category}</td>
                      <td className="mms-hint">{row.unit || '—'}</td>
                      <td className="mms-hint">{row.description || '—'}</td>
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
        )}
      </div>

      {showForm ? (
        <Modal
          title={editing ? `Edit ${editing.code}` : 'New equipment type'}
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="equipment-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save type'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="equipment-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-4">
                <label className="form-label" htmlFor="eq-code">
                  Code *
                </label>
                <input
                  id="eq-code"
                  className="form-control form-control-sm"
                  value={form.code}
                  onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
                  required
                  maxLength={30}
                />
              </div>
              <div className="col-md-8">
                <label className="form-label" htmlFor="eq-name">
                  Name *
                </label>
                <input
                  id="eq-name"
                  className="form-control form-control-sm"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  required
                  maxLength={120}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="eq-category">
                  Category *
                </label>
                <input
                  id="eq-category"
                  className="form-control form-control-sm"
                  value={form.category}
                  onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                  required
                  maxLength={80}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="eq-unit">
                  Unit
                </label>
                <input
                  id="eq-unit"
                  className="form-control form-control-sm"
                  value={form.unit}
                  onChange={(e) => setForm((f) => ({ ...f, unit: e.target.value }))}
                  maxLength={20}
                />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="eq-description">
                  Description
                </label>
                <input
                  id="eq-description"
                  className="form-control form-control-sm"
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  maxLength={255}
                />
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {confirmDelete ? (
        <Modal
          title="Delete equipment type"
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
            Delete equipment type <strong>{confirmDelete.code}</strong> · {confirmDelete.name}?
          </p>
        </Modal>
      ) : null}
    </>
  )
}
