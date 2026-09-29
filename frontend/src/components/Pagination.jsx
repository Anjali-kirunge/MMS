export default function Pagination({ page, size, totalElements, onPageChange }) {
  const totalPages = size > 0 ? Math.ceil(totalElements / size) : 0
  if (totalPages <= 1) {
    return (
      <div className="d-flex justify-content-between align-items-center small text-muted">
        <span>{totalElements} record(s)</span>
      </div>
    )
  }
  return (
    <div className="d-flex justify-content-between align-items-center">
      <span className="small text-muted">
        Page {page + 1} of {totalPages} · {totalElements} record(s)
      </span>
      <div className="btn-group btn-group-sm">
        <button
          type="button"
          className="btn btn-outline-secondary"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <button
          type="button"
          className="btn btn-outline-secondary"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}
