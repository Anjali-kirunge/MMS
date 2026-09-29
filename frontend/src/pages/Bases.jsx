import { useEffect, useState } from 'react'
import { api } from '../api/client.js'
import Modal from '../components/Modal.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const emptyForm = () => ({ code: '', name: '', location: '', commander: '' })

export default function Bases() {
  const [rows, setRows] = useState(null)
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

  const load = () => {
    setLoading(true)
    return api
      .bases()
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
    setForm({ code: row.code, name: row.name, location: row.location || '', commander: row.commander || '' })
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
        location: form.location || null,
        commander: form.commander || null,
      }
      if (editing) {
        await api.updateBase(editing.id, payload)
        setFlash({ variant: 'success', message: `Base ${form.code} updated.` })
      } else {
        await api.createBase(payload)
        setFlash({ variant: 'success', message: `Base ${form.code} created.` })
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
      await api.deleteBase(confirmDelete.id)
      setFlash({ variant: 'success', message: `Base ${confirmDelete.code} deleted.` })
      setConfirmDelete(null)
      load()
    } catch (err) {
      setActionError(err)
    }
  }

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="mb-0">Military bases</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openCreate}>
            New base
          </button>
        </div>
        <p className="mms-hint mb-0">
          Bases scope every transaction. A base that already holds stock, personnel or transaction history cannot be
          deleted.
        </p>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      {actionError ? <ErrorAlert error={actionError} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !rows ? (
          <Spinner label="Loading bases…" />
        ) : (
          <div className="table-responsive">
            <table className="table table-sm mms-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Location</th>
                  <th>Commander</th>
                  <th>Created</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {!rows || rows.length === 0 ? (
                  <EmptyRow colSpan={6} label="No bases defined yet." />
                ) : (
                  rows.map((row) => (
                    <tr key={row.id}>
                      <td className="fw-semibold">{row.code}</td>
                      <td>{row.name}</td>
                      <td className="mms-hint">{row.location || '—'}</td>
                      <td className="mms-hint">{row.commander || '—'}</td>
                      <td className="mms-hint">{row.createdAt?.slice(0, 10)}</td>
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
          title={editing ? `Edit ${editing.code}` : 'New base'}
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="base-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save base'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="base-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-4">
                <label className="form-label" htmlFor="b-code">
                  Code *
                </label>
                <input
                  id="b-code"
                  className="form-control form-control-sm"
                  value={form.code}
                  onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
                  required
                  maxLength={20}
                />
              </div>
              <div className="col-md-8">
                <label className="form-label" htmlFor="b-name">
                  Name *
                </label>
                <input
                  id="b-name"
                  className="form-control form-control-sm"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  required
                  maxLength={120}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="b-location">
                  Location
                </label>
                <input
                  id="b-location"
                  className="form-control form-control-sm"
                  value={form.location}
                  onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
                  maxLength={200}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="b-commander">
                  Commander
                </label>
                <input
                  id="b-commander"
                  className="form-control form-control-sm"
                  value={form.commander}
                  onChange={(e) => setForm((f) => ({ ...f, commander: e.target.value }))}
                  maxLength={120}
                />
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {confirmDelete ? (
        <Modal
          title="Delete base"
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
            Delete base <strong>{confirmDelete.code}</strong> · {confirmDelete.name}?
          </p>
        </Modal>
      ) : null}
    </>
  )
}
