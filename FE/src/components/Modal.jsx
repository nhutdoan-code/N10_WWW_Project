export default function Modal({ title, onClose, children, wide = false }) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className={`modal${wide ? ' modal-wide' : ''}`} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="btn btn-ghost" onClick={onClose}>✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}
