import { useEffect, useMemo, useState } from 'react';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import {
  AFTER_ACTIONS, RETURN_ACTIONS, GROUPS, STATUS_LABELS, TYPE_LABELS,
  countPending, filterRequests, formatAdminDate, normalizeRequests,
} from '../../utils/afterSalesAdmin';

const initial = { query: '', status: '', type: '', from: '', to: '', sort: 'priority' };
const ACTIONS = { return: RETURN_ACTIONS, after: AFTER_ACTIONS };

export default function AdminReturnsPage() {
  const { data, loading, error, reload } = useFetch(async () => {
    const [returns, afterSales] = await Promise.all([adminService.returns(), adminService.afterSales()]);
    return { returns: returns || [], afterSales: afterSales || [] };
  });
  const [filters, setFilters] = useState(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const [internalNote, setInternalNote] = useState('');
  const [loadingInternal, setLoadingInternal] = useState(false);
  const [savingInternal, setSavingInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const rows = useMemo(() => normalizeRequests(data), [data]);
  const results = useMemo(() => filterRequests(rows, filters), [rows, filters]);
  const pageCount = Math.max(1, Math.ceil(results.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visible = results.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const detail = selected ? rows.find(r => r.source === selected.source && r.maYeuCau === selected.maYeuCau) : null;
  useEffect(() => { setPage(1); }, [filters, pageSize]);
  useEffect(() => {
    setNote('');
    setInternalNote('');
    if (!selected) return;
    let alive = true;
    setLoadingInternal(true);
    adminService.getInternalAfterSalesNote(selected.source, selected.maYeuCau)
      .then(payload => { if (alive) setInternalNote(payload?.ghiChu || ''); })
      .catch(() => { if (alive) toast('Không tải được ghi chú nội bộ', 'error'); })
      .finally(() => { if (alive) setLoadingInternal(false); });
    return () => { alive = false; };
  }, [selected]);
  const saveInternal = async () => {
    if (!detail || savingInternal) return;
    setSavingInternal(true);
    try {
      await adminService.saveInternalAfterSalesNote(detail.source, detail.maYeuCau, internalNote);
      toast('Đã lưu ghi chú nội bộ');
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setSavingInternal(false); }
  };
  const setFilter = (key, value) => setFilters(s => ({ ...s, [key]: value }));
  const statusSort = () => setFilter('sort', filters.sort === 'statusAsc' ? 'statusDesc' : 'statusAsc');
  const dateSort = () => setFilter('sort', filters.sort === 'dateDesc' ? 'dateAsc' : 'dateDesc');
  const doneToday = rows.filter(r => GROUPS.completed.includes(r.trangThai) &&
    String(r.ngayCapNhat || '').slice(0,10) === new Date().getFullYear() + '-' + String(new Date().getMonth()+1).padStart(2,'0') + '-' + String(new Date().getDate()).padStart(2,'0')).length;
  const applyAction = async (r, next) => {
    if (busy) return;
    if (next === 'TU_CHOI' && !note.trim()) { toast('Nhập lý do từ chối trước khi xác nhận.', 'error'); return; }
    if (next === 'HOAN_TAT' && !window.confirm('Xác nhận đã xử lý xong yêu cầu này? Thao tác này không tự hoàn tiền.')) return;
    setBusy(true);
    try {
      if (r.source === 'return') await adminService.updateReturn(r.maYeuCau, next, note.trim());
      else await adminService.updateAfterSale(r.maYeuCau, next, note.trim());
      toast('Đã cập nhật trạng thái yêu cầu');
      setNote('');
      reload();
      window.dispatchEvent(new Event('phonenest:after-sales-updated'));
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setBusy(false); }
  };

  return <div className="after-sales-admin">
    <h2>Quản lý hậu mãi</h2>
    <p className="muted">Quản lý yêu cầu đổi/trả, bảo hành và khiếu nại. Hoàn tất không đồng nghĩa đã chuyển tiền hoàn thực tế.</p>
    {loading && <p className="muted">Đang tải dữ liệu...</p>}
    {error && <p className="error">{error} <button className="btn" onClick={reload}>Tải lại</button></p>}
    {!loading && !error && data && <>
      <div className="as-stats">
        <div><span>Chờ xử lý</span><strong>{countPending(rows)}</strong></div>
        <div><span>Đang xử lý</span><strong>{rows.filter(r => GROUPS.processing.includes(r.trangThai)).length}</strong></div>
        <div><span>Hoàn tất hôm nay</span><strong>{doneToday}</strong></div>
        <div><span>Tổng yêu cầu</span><strong>{rows.length}</strong></div>
      </div>
      <div className="as-filters">
        <label className="as-search">Tìm mã yêu cầu, mã đơn, tên khách, SĐT
          <input aria-label="Tìm kiếm hậu mãi" placeholder="Ví dụ: YC001, HM002, DH003, Nguyễn Văn A..." value={filters.query} onChange={e => setFilter('query',e.target.value)} />
        </label>
        <label>Trạng thái<select value={filters.status} onChange={e=>setFilter('status',e.target.value)}>
          <option value="">Tất cả</option><option value="waiting">Chờ xử lý</option><option value="processing">Đang xử lý</option><option value="completed">Hoàn tất</option><option value="rejected">Từ chối</option>
        </select></label>
        <label>Loại yêu cầu<select value={filters.type} onChange={e=>setFilter('type',e.target.value)}>
          <option value="">Tất cả</option>{Object.entries(TYPE_LABELS).map(([key,label])=><option key={key} value={key}>{label}</option>)}
        </select></label>
        <label>Từ ngày<input aria-label="Từ ngày" type="date" value={filters.from} max={filters.to || undefined} onChange={e=>setFilter('from',e.target.value)} /></label>
        <label>Đến ngày<input aria-label="Đến ngày" type="date" value={filters.to} min={filters.from || undefined} onChange={e=>setFilter('to',e.target.value)} /></label>
        <label>Sắp xếp<select value={filters.sort} onChange={e=>setFilter('sort',e.target.value)}>
          <option value="priority">Ưu tiên chưa xử lý · mới nhất</option><option value="dateDesc">Ngày mới nhất</option><option value="dateAsc">Ngày cũ nhất</option><option value="statusAsc">Chờ xử lý trước</option><option value="statusDesc">Hoàn tất trước</option>
        </select></label>
        <button className="btn" onClick={() => setFilters(initial)}>Xóa bộ lọc</button>
      </div>
      <div className="as-list-top"><h3>Danh sách yêu cầu ({results.length})</h3><label>Hiển thị <select value={pageSize} onChange={e=>setPageSize(Number(e.target.value))}><option value={10}>10</option><option value={20}>20</option></select> dòng / trang</label></div>
      <div className="after-sales-table-wrap"><table className="table as-table">
        <thead><tr><th>Mã yêu cầu</th><th>Đơn hàng</th><th>Khách hàng</th><th>Loại / sản phẩm</th><th><button className="as-sort" onClick={dateSort}>Ngày ↕</button></th><th><button className="as-sort" onClick={statusSort}>Trạng thái ↕</button></th><th>Xử lý</th></tr></thead>
        <tbody>{visible.length===0 && <tr><td colSpan="7" className="as-empty">Không có yêu cầu phù hợp bộ lọc.</td></tr>}
          {visible.map(r => <tr key={`${r.source}-${r.maYeuCau}`} className="as-clickable" onClick={()=>setSelected({source:r.source,maYeuCau:r.maYeuCau})}>
            <td><strong>{r.maYeuCau}</strong></td><td>{r.maDonHang}</td><td>{r.tenKhach}{r.soDienThoai && <div className="muted">{r.soDienThoai}</div>}</td>
            <td>{TYPE_LABELS[r.loai] || r.loai}<div className="muted">{r.tenSanPham || 'Toàn đơn'}{r.soLuong ? ` · SL ${r.soLuong}` : ''}</div></td>
            <td>{formatAdminDate(r.ngayTao)}</td><td><span className={`as-status as-${r.trangThai}`}>{STATUS_LABELS[r.trangThai] || r.trangThai}</span></td>
            <td><button className="btn" onClick={e=>{e.stopPropagation();setSelected({source:r.source,maYeuCau:r.maYeuCau});}}>{(ACTIONS[r.source][r.trangThai] || []).length ? 'Xử lý' : 'Xem chi tiết'}</button></td>
          </tr>)}</tbody></table></div>
      <div className="as-pagination"><span>Trang {currentPage}/{pageCount} · {results.length} yêu cầu</span><button className="btn" disabled={currentPage===1} onClick={()=>setPage(1)}>Đầu</button><button className="btn" disabled={currentPage===1} onClick={()=>setPage(p=>p-1)}>Trước</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(p=>p+1)}>Sau</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(pageCount)}>Cuối</button></div>
    </>}
    {detail && <div className="as-overlay" onMouseDown={e=>{if(e.target===e.currentTarget)setSelected(null)}}>
      <section className="as-modal" role="dialog" aria-modal="true" aria-label={`Chi tiết yêu cầu ${detail.maYeuCau}`}>
        <div className="as-modal-header"><h3>Yêu cầu {detail.maYeuCau}</h3><button className="btn" onClick={()=>setSelected(null)} aria-label="Đóng chi tiết">✕</button></div>
        <div className="as-detail-grid"><div><b>Đơn hàng</b><p>{detail.maDonHang}</p></div><div><b>Khách hàng</b><p>{detail.tenKhach}</p></div>
          <div><b>Số điện thoại</b><p>{detail.soDienThoai || 'Chưa có'}</p></div><div><b>Ngày yêu cầu</b><p>{formatAdminDate(detail.ngayTao)}</p></div>
          <div><b>Loại yêu cầu</b><p>{TYPE_LABELS[detail.loai] || detail.loai}</p></div><div><b>Trạng thái</b><p><span className={`as-status as-${detail.trangThai}`}>{STATUS_LABELS[detail.trangThai] || detail.trangThai}</span></p></div></div>
        <p><b>Sản phẩm:</b> {detail.tenSanPham || 'Toàn đơn'} {detail.soLuong ? `· Số lượng ${detail.soLuong}` : ''}</p>
        <p><b>Lý do / mô tả:</b> {detail.lyDo}</p>
        {detail.hinhAnhMinhChung && <div><b>Ảnh minh chứng</b><p><a href={detail.hinhAnhMinhChung} target="_blank" rel="noreferrer"><img className="as-proof" src={detail.hinhAnhMinhChung} alt="Bằng chứng khách gửi" /></a></p></div>}
        <p><b>Phản hồi đã lưu:</b> {detail.ghiChuXuLy || 'Chưa có'}</p>
        <div className="as-review"><label>Ghi chú nội bộ (chỉ Admin thấy)
          <textarea rows="3" maxLength={1000} placeholder="Thông tin trao đổi nội bộ, không gửi cho khách hàng" value={internalNote} disabled={loadingInternal} onChange={e=>setInternalNote(e.target.value)} />
        </label><button className="btn" disabled={savingInternal || loadingInternal} onClick={saveInternal}>{savingInternal ? 'Đang lưu...' : 'Lưu ghi chú nội bộ'}</button></div>
        {detail.lichSu?.length > 0 && <div><b>Lịch sử xử lý</b><ol className="as-history">{detail.lichSu.map((h,i)=><li key={i}>{formatAdminDate(h.thoiGian)} · {STATUS_LABELS[h.trangThai] || h.trangThai} {h.ghiChu ? `· ${h.ghiChu}` : ''}</li>)}</ol></div>}
        {(ACTIONS[detail.source][detail.trangThai] || []).length > 0 && <div className="as-review">
          <label>Ghi chú xử lý / lý do từ chối (khách hàng có thể nhìn thấy)<textarea rows="3" maxLength={500} placeholder="Nhập ghi chú hoặc lý do từ chối..." value={note} onChange={e=>setNote(e.target.value)}/></label>
          <div className="after-sales-actions">{ACTIONS[detail.source][detail.trangThai].map(([next,label]) =>
            <button key={next} className="btn" disabled={busy} onClick={()=>applyAction(detail,next)}>{busy ? 'Đang lưu...' : label}</button>)}</div>
        </div>}
        <button className="btn" onClick={()=>setSelected(null)}>Đóng</button>
      </section>
    </div>}
  </div>;
}
