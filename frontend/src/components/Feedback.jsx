export function ErrorAlert({ error }) {
  if (!error) return null
  const fields = error.fieldErrors || {}
  return (
    <div className="alert alert-danger py-2" role="alert">
      <div className="fw-semibold">{error.message}</div>
      {Object.keys(fields).length > 0 ? (
        <ul className="mb-0 mt-1 small">
          {Object.entries(fields).map(([field, message]) => (
            <li key={field}>
              <code>{field}</code>: {message}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

export function Flash({ flash, onDismiss }) {
  if (!flash) return null
  return (
    <div className={`alert alert-${flash.variant || 'success'} py-2 alert-dismissible fade show`} role="alert">
      {flash.message}
      <button type="button" className="btn-close" onClick={onDismiss} aria-label="Close" />
    </div>
  )
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="text-center py-4 text-muted">
      <div className="spinner-border spinner-border-sm me-2" role="status" />
      {label}
    </div>
  )
}

export function EmptyRow({ colSpan, label = 'No records found.' }) {
  return (
    <tr>
      <td colSpan={colSpan} className="text-center text-muted py-4">
        {label}
      </td>
    </tr>
  )
}
