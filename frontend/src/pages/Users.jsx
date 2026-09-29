import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import useReferenceData from '../hooks/useReferenceData.js'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyRow, ErrorAlert, Flash, Spinner } from '../components/Feedback.jsx'

const PAGE_SIZE = 15
const ROLES = ['ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER']

const emptyForm = () => ({
  username: '',
  password: '',
  fullName: '',
  email: '',
  role: 'LOGISTICS_OFFICER',
  baseId: '',
  enabled: true,
})

export default function Users() {
  const { user: currentUser } = useAuth()
  const { bases } = useReferenceData()

  const [filters, setFilters] = useState({ role: '', baseId: '' })
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
  const [resetting, setResetting] = useState(null)
  const [newPassword, setNewPassword] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.users({ ...filters, page, size: PAGE_SIZE })
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
      username: row.username,
      password: '',
      fullName: row.fullName,
      email: row.email || '',
      role: row.role,
      baseId: row.baseId ? String(row.baseId) : '',
      enabled: row.enabled !== false,
    })
    setFormError(null)
    setShowForm(true)
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      if (editing) {
        await api.updateUser(editing.id, {
          fullName: form.fullName,
          email: form.email || null,
          role: form.role,
          baseId: form.baseId ? Number(form.baseId) : null,
          enabled: form.enabled,
        })
        setFlash({ variant: 'success', message: `User ${editing.username} updated.` })
      } else {
        await api.createUser({
          username: form.username,
          password: form.password,
          fullName: form.fullName,
          email: form.email || null,
          role: form.role,
          baseId: form.baseId ? Number(form.baseId) : null,
          enabled: form.enabled,
        })
        setFlash({ variant: 'success', message: `User ${form.username} created.` })
      }
      setShowForm(false)
      setPage(0)
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  const toggleEnabled = async (row) => {
    setActionError(null)
    try {
      await api.setUserEnabled(row.id, !row.enabled)
      setFlash({
        variant: 'success',
        message: `${row.username} ${row.enabled ? 'disabled' : 'enabled'}.`,
      })
      load()
    } catch (err) {
      setActionError(err)
    }
  }

  const remove = async () => {
    if (!confirmDelete) return
    setActionError(null)
    try {
      await api.deleteUser(confirmDelete.id)
      setFlash({ variant: 'success', message: `User ${confirmDelete.username} deleted.` })
      setConfirmDelete(null)
      load()
    } catch (err) {
      setActionError(err)
    }
  }

  const submitReset = async (event) => {
    event.preventDefault()
    setActionError(null)
    try {
      await api.resetPassword(resetting.id, newPassword)
      setFlash({ variant: 'success', message: `Password reset for ${resetting.username}.` })
      setResetting(null)
      setNewPassword('')
    } catch (err) {
      setActionError(err)
    }
  }

  const rows = data?.content || []

  return (
    <>
      <div className="mms-card">
        <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <h5 className="mb-0">User management</h5>
          <button type="button" className="btn btn-sm btn-primary" onClick={openCreate}>
            New user
          </button>
        </div>

        <div className="row g-2 align-items-end">
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="u-role">
              Role
            </label>
            <select
              id="u-role"
              className="form-select form-select-sm"
              value={filters.role}
              onChange={(e) => updateFilter('role')(e.target.value)}
            >
              <option value="">All roles</option>
              {ROLES.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label" htmlFor="u-base">
              Base
            </label>
            <select
              id="u-base"
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
          <div className="col-12 col-md-6 mms-hint">
            BASE_COMMANDER accounts are always bound to a base; LOGISTICS_OFFICER accounts may be unbound.
          </div>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}
      {actionError ? <ErrorAlert error={actionError} /> : null}
      <Flash flash={flash} onDismiss={() => setFlash(null)} />

      <div className="mms-card">
        {loading && !data ? (
          <Spinner label="Loading users…" />
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-sm mms-table">
                <thead>
                  <tr>
                    <th>Username</th>
                    <th>Full name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Base</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <EmptyRow colSpan={8} label="No users match these filters." />
                  ) : (
                    rows.map((row) => {
                      const isSelf = currentUser && currentUser.id === row.id
                      return (
                        <tr key={row.id}>
                          <td className="fw-semibold">
                            {row.username}
                            {isSelf ? <span className="badge text-bg-light ms-1">you</span> : null}
                          </td>
                          <td>{row.fullName}</td>
                          <td className="mms-hint">{row.email || '—'}</td>
                          <td>
                            <span className="badge text-bg-dark">{row.role}</span>
                          </td>
                          <td>{row.baseCode ? `${row.baseCode} · ${row.baseName}` : '—'}</td>
                          <td>
                            <span className={`badge ${row.enabled ? 'text-bg-success' : 'text-bg-secondary'}`}>
                              {row.enabled ? 'ENABLED' : 'DISABLED'}
                            </span>
                          </td>
                          <td className="mms-hint">{row.createdAt?.slice(0, 10)}</td>
                          <td className="text-end text-nowrap">
                            <button type="button" className="btn btn-sm btn-outline-secondary me-1" onClick={() => openEdit(row)}>
                              Edit
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-secondary me-1"
                              onClick={() => {
                                setResetting(row)
                                setNewPassword('')
                                setActionError(null)
                              }}
                            >
                              Password
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-secondary me-1"
                              onClick={() => toggleEnabled(row)}
                              disabled={isSelf}
                            >
                              {row.enabled ? 'Disable' : 'Enable'}
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-danger"
                              onClick={() => {
                                setConfirmDelete(row)
                                setActionError(null)
                              }}
                              disabled={isSelf}
                            >
                              Delete
                            </button>
                          </td>
                        </tr>
                      )
                    })
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
          title={editing ? `Edit ${editing.username}` : 'Create user'}
          onClose={() => setShowForm(false)}
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" form="user-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save user'}
              </button>
            </>
          }
        >
          {formError ? <ErrorAlert error={formError} /> : null}
          <form id="user-form" onSubmit={submit}>
            <div className="row g-2">
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-username">
                  Username *
                </label>
                <input
                  id="uf-username"
                  className="form-control form-control-sm"
                  value={form.username}
                  disabled={Boolean(editing)}
                  onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
                  required
                  minLength={3}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-password">
                  Password {editing ? '' : '*'}
                </label>
                <input
                  id="uf-password"
                  type="password"
                  className="form-control form-control-sm"
                  value={form.password}
                  onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                  required={!editing}
                  minLength={6}
                  autoComplete="new-password"
                />
                {editing ? (
                  <div className="form-text">Use the Password button in the table to reset an existing password.</div>
                ) : null}
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-fullname">
                  Full name *
                </label>
                <input
                  id="uf-fullname"
                  className="form-control form-control-sm"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                  required
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-email">
                  Email
                </label>
                <input
                  id="uf-email"
                  type="email"
                  className="form-control form-control-sm"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-role">
                  Role *
                </label>
                <select
                  id="uf-role"
                  className="form-select form-select-sm"
                  value={form.role}
                  onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
                  required
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="uf-base">
                  Base
                </label>
                <select
                  id="uf-base"
                  className="form-select form-select-sm"
                  value={form.baseId}
                  onChange={(e) => setForm((f) => ({ ...f, baseId: e.target.value }))}
                  required={form.role === 'BASE_COMMANDER'}
                >
                  <option value="">No base (global access)</option>
                  {bases.map((base) => (
                    <option key={base.id} value={base.id}>
                      {base.code} · {base.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-12">
                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="uf-enabled"
                    checked={form.enabled}
                    onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
                  />
                  <label className="form-check-label" htmlFor="uf-enabled">
                    Account enabled
                  </label>
                </div>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {resetting ? (
        <Modal
          title={`Reset password · ${resetting.username}`}
          onClose={() => setResetting(null)}
          size="modal-sm"
          footer={
            <>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setResetting(null)}>
                Cancel
              </button>
              <button type="submit" form="reset-form" className="btn btn-primary">
                Reset password
              </button>
            </>
          }
        >
          {actionError ? <ErrorAlert error={actionError} /> : null}
          <form id="reset-form" onSubmit={submitReset}>
            <label className="form-label" htmlFor="new-password">
              New password
            </label>
            <input
              id="new-password"
              type="password"
              className="form-control form-control-sm"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              minLength={6}
              required
              autoComplete="new-password"
            />
          </form>
        </Modal>
      ) : null}

      {confirmDelete ? (
        <Modal
          title="Delete user"
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
            Delete account <strong>{confirmDelete.username}</strong>? Historical records keep the username, but the
            account can no longer sign in.
          </p>
        </Modal>
      ) : null}
    </>
  )
}
