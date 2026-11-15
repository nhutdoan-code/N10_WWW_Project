export default function AdminPagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  loading = false,
}) {
  const pageCount = totalPages || 0;

  return (
    <div className="row between" style={{ gap: '12px', margin: '16px 0', alignItems: 'center' }}>
      <span className="muted">
        {totalElements || 0} kết quả · Trang {pageCount ? page + 1 : 0}/{pageCount}
      </span>
      <div className="row" style={{ gap: '8px', alignItems: 'center' }}>
        <label className="row" style={{ gap: '6px', alignItems: 'center' }}>
          <span className="muted">Mỗi trang</span>
          <select aria-label="Số dòng mỗi trang" value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
          </select>
        </label>
        <button className="btn" disabled={loading || page <= 0} onClick={() => onPageChange(page - 1)}>
          Trước
        </button>
        <button className="btn" disabled={loading || !pageCount || page + 1 >= pageCount} onClick={() => onPageChange(page + 1)}>
          Sau
        </button>
      </div>
    </div>
  );
}
