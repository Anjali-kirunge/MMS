import { useEffect } from 'react'

export default function Modal({ title, children, footer, onClose, size = '' }) {
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
    }
  }, [onClose])

  return (
    <div className="mms-modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div className={`mms-modal ${size}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="mms-modal-header">
          <h5>{title}</h5>
          <button type="button" className="btn-close" aria-label="Close" onClick={onClose} />
        </div>
        <div className="mms-modal-body">{children}</div>
        {footer ? <div className="mms-modal-footer">{footer}</div> : null}
      </div>
    </div>
  )
}
